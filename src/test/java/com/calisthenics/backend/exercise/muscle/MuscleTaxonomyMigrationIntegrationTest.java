package com.calisthenics.backend.exercise.muscle;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MuscleTaxonomyMigrationIntegrationTest {

    @Test
    void v58MigrationCreatesTaxonomySeedsCanonicalExercisesAndIsIdempotentInH2PostgresMode() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flyway_v58_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Create exercises table matching V1 + V6 + V22 + V54 + V55 schema before V58 runs
        jdbc.execute("""
                CREATE TABLE exercises (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    code VARCHAR(50) NOT NULL UNIQUE,
                    category VARCHAR(50) NOT NULL,
                    difficulty VARCHAR(50) NOT NULL,
                    description TEXT,
                    target_muscles TEXT,
                    instructions TEXT,
                    image_url VARCHAR(500),
                    video_url VARCHAR(500),
                    equipment VARCHAR(255),
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
                    origin VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
                    training_type VARCHAR(32) NOT NULL DEFAULT 'CALISTHENICS',
                    secondary_muscles TEXT,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
                )
                """);

        List<String> standardCodes = List.of(
                "PUSH_UP", "DIPS", "PIKE_PUSH_UP", "PULL_UP", "MUSCLE_UP", "SQUAT", "L_SIT",
                "HANDSTAND", "TUCK_PLANCHE", "ADV_TUCK_PLANCHE", "STRADDLE_PLANCHE",
                "FULL_PLANCHE", "PLANCHE", "FRONT_LEVER", "FRONT_LEVER_RAISES"
        );
        for (String code : standardCodes) {
            jdbc.update(
                    "INSERT INTO exercises (name, code, category, difficulty, target_muscles) VALUES (?, ?, 'SKILL', 'INTERMEDIATE', 'Legacy')",
                    code, code
            );
        }

        // Custom exercise with overlapping primary & secondary keyword ("Triceps")
        jdbc.update("""
                INSERT INTO exercises (name, code, category, difficulty, target_muscles, secondary_muscles)
                VALUES ('Decline Ring Push-Up', 'DECLINE_RING_PUSH_UP', 'PUSH', 'INTERMEDIATE',
                        'Chest, Triceps', 'Triceps, Anterior Deltoids, Core')
                """);
        // Custom exercise targeting Middle Trapezius specifically (must not falsely match Upper Trapezius)
        jdbc.update("""
                INSERT INTO exercises (name, code, category, difficulty, target_muscles, secondary_muscles)
                VALUES ('Band Face Pull', 'BAND_FACE_PULL', 'PULL', 'BEGINNER',
                        'Rear Deltoids, Middle Trapezius', 'Forearms')
                """);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("57")
                .load();
        var migrateResult = flyway.migrate();

        assertThat(migrateResult.migrationsExecuted).isGreaterThanOrEqualTo(1);

        Integer v58Count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '58' AND \"success\" = TRUE",
                Integer.class
        );
        assertThat(v58Count).isEqualTo(1);

        Integer muscleGroupCount = jdbc.queryForObject("SELECT COUNT(*) FROM muscle_groups", Integer.class);
        assertThat(muscleGroupCount).isEqualTo(22);

        // Verify all 15 canonical exercises got mapped in DB
        for (String code : standardCodes) {
            Integer targetCount = jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM exercise_muscle_targets emt
                    JOIN exercises e ON e.id = emt.exercise_id
                    WHERE e.code = ?
                    """,
                    Integer.class,
                    code
            );
            assertThat(targetCount).as("seeded targets for %s", code).isGreaterThan(0);
        }

        // Verify backfill for custom exercise kept PRIMARY precedence on 'triceps'
        List<Map<String, Object>> customTargets = jdbc.queryForList(
                """
                SELECT emt.muscle_id, emt.role FROM exercise_muscle_targets emt
                JOIN exercises e ON e.id = emt.exercise_id
                WHERE e.code = 'DECLINE_RING_PUSH_UP'
                ORDER BY emt.display_order ASC
                """
        );
        assertThat(customTargets).extracting(r -> r.get("MUSCLE_ID").toString())
                .containsExactlyInAnyOrder("chest", "triceps", "front_deltoids", "rectus_abdominis", "transverse_abdominis");
        assertThat(customTargets.stream()
                .filter(r -> "triceps".equals(r.get("MUSCLE_ID")))
                .findFirst()
                .orElseThrow()
                .get("ROLE")).isEqualTo("PRIMARY");

        // Verify Middle Trapezius backfill did not falsely trigger Upper Trapezius
        List<String> facePullMuscles = jdbc.queryForList(
                """
                SELECT emt.muscle_id FROM exercise_muscle_targets emt
                JOIN exercises e ON e.id = emt.exercise_id
                WHERE e.code = 'BAND_FACE_PULL'
                """,
                String.class
        );
        assertThat(facePullMuscles).containsExactlyInAnyOrder("rear_deltoids", "middle_trapezius", "forearms");

        int totalRowsBeforeReRun = jdbc.queryForObject("SELECT COUNT(*) FROM exercise_muscle_targets", Integer.class);

        // Re-executing V58 script must remain 100% idempotent
        try (Connection conn = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    conn,
                    new ClassPathResource("db/migration/V58__add_muscle_taxonomy_and_exercise_muscle_targets.sql")
            );
        }
        int totalRowsAfterReRun = jdbc.queryForObject("SELECT COUNT(*) FROM exercise_muscle_targets", Integer.class);
        assertThat(totalRowsAfterReRun).isEqualTo(totalRowsBeforeReRun);

        // Verify role check constraint & unique constraint
        Long pushUpId = jdbc.queryForObject("SELECT id FROM exercises WHERE code = 'PUSH_UP'", Long.class);
        assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO exercise_muscle_targets (exercise_id, muscle_id, role, display_order) VALUES (?, 'biceps', 'INVALID', 9)",
                pushUpId
        ));
        assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO exercise_muscle_targets (exercise_id, muscle_id, role, display_order) VALUES (?, 'chest', 'SECONDARY', 9)",
                pushUpId
        ));
    }
}
