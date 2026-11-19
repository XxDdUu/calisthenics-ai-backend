package com.calisthenics.backend.skill;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogMigrationIntegrationTest {

    @Test
    void v59MigrationSeedsCategoriesSkillsProgressionsAndLinksWithoutDuplicatesWhenExercisesAlreadyExist() {
        String dbName = "skill_taxonomy_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE exercises (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    code VARCHAR(50) NOT NULL UNIQUE,
                    category VARCHAR(30) NOT NULL,
                    difficulty VARCHAR(30) NOT NULL,
                    description TEXT,
                    target_muscles VARCHAR(255),
                    secondary_muscles VARCHAR(255),
                    instructions TEXT,
                    image_url VARCHAR(500),
                    video_url VARCHAR(500),
                    equipment VARCHAR(255),
                    exercise_type VARCHAR(50),
                    recommended_sets INT,
                    recommended_reps VARCHAR(50),
                    recommended_rest_seconds INT,
                    common_mistakes TEXT,
                    safety_considerations TEXT,
                    progression_notes TEXT,
                    aliases TEXT,
                    active BOOLEAN NOT NULL DEFAULT TRUE,
                    status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
                    origin VARCHAR(32) NOT NULL DEFAULT 'SEEDED',
                    training_type VARCHAR(32) NOT NULL DEFAULT 'CALISTHENICS',
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        // Pre-insert an exercise matching one of V59's seeded names with a custom code
        // to prove V59 does NOT create duplicate exercise rows by name, plus Pike Push-Up for multi-skill test.
        jdbc.update("""
                INSERT INTO exercises (name, code, category, difficulty, description, target_muscles, instructions, equipment, active, status, origin, training_type)
                VALUES
                ('Barbell Bench Press', 'CUSTOM_BENCH_PRESS', 'CHEST', 'BEGINNER', 'Existing bench press', 'Chest', 'Press bar', 'Barbell', TRUE, 'PUBLISHED', 'SEEDED', 'GYM'),
                ('Pike Push-Up', 'PIKE_PUSH_UP', 'PUSH', 'BEGINNER', 'Existing pike push-up', 'Shoulders', 'Press in pike', 'Floor', TRUE, 'PUBLISHED', 'SEEDED', 'CALISTHENICS')
                """);

        // Run V58 and V59 via Flyway baseline at V57
        Flyway upToV59 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("57")
                .load();
        upToV59.migrate();

        Integer totalCategories = jdbc.queryForObject("SELECT COUNT(*) FROM skill_categories", Integer.class);
        assertThat(totalCategories).isEqualTo(12);

        Integer calCategories = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skill_categories WHERE training_type = 'CALISTHENICS'", Integer.class);
        Integer gymCategories = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skill_categories WHERE training_type = 'GYM'", Integer.class);
        assertThat(calCategories).isEqualTo(6);
        assertThat(gymCategories).isEqualTo(6);

        Integer calSkills = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skills WHERE training_type = 'CALISTHENICS'", Integer.class);
        Integer gymSkills = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skills WHERE training_type = 'GYM'", Integer.class);
        assertThat(calSkills).isEqualTo(22);
        assertThat(gymSkills).isEqualTo(23);

        // Verify Muscle-up appears only once in skills table, but has memberships in both PULL and SKILLS
        Integer muscleUpSkillRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM skills WHERE code = 'CAL_MUSCLE_UP'", Integer.class);
        assertThat(muscleUpSkillRows).isEqualTo(1);

        List<String> muscleUpCategories = jdbc.queryForList(
                """
                SELECT m.category_key FROM skill_category_memberships m
                JOIN skills s ON s.id = m.skill_id
                WHERE s.code = 'CAL_MUSCLE_UP'
                ORDER BY m.is_primary DESC, m.category_key ASC
                """,
                String.class
        );
        assertThat(muscleUpCategories).containsExactlyInAnyOrder("PULL", "SKILLS");

        // Verify NO duplicate Barbell Bench Press was inserted
        Integer benchCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exercises WHERE LOWER(name) = 'barbell bench press'", Integer.class);
        assertThat(benchCount).isEqualTo(1);

        // Verify CUSTOM_BENCH_PRESS was linked to GYM_BENCH_PRESS by name match without duplication
        Integer linkedCustomBench = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM skill_exercise_links l
                JOIN skills s ON s.id = l.skill_id
                JOIN exercises e ON e.id = l.exercise_id
                WHERE s.code = 'GYM_BENCH_PRESS' AND e.code = 'CUSTOM_BENCH_PRESS'
                """,
                Integer.class
        );
        assertThat(linkedCustomBench).isEqualTo(1);

        // Verify shared exercise supports multiple skills (Pike Push-Up links to both HSPU and Handstand)
        List<String> pikeSkills = jdbc.queryForList(
                """
                SELECT s.code FROM skill_exercise_links l
                JOIN skills s ON s.id = l.skill_id
                JOIN exercises e ON e.id = l.exercise_id
                WHERE UPPER(e.code) = 'PIKE_PUSH_UP' OR LOWER(e.name) = 'pike push-up'
                """,
                String.class
        );
        assertThat(pikeSkills).contains("CAL_HSPU", "CAL_HANDSTAND");
    }
}
