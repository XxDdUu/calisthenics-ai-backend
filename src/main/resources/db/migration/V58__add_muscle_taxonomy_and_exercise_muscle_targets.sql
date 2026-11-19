-- V58: Canonical 22-muscle anatomical taxonomy and normalized exercise muscle targets

CREATE TABLE IF NOT EXISTS muscle_groups (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    region VARCHAR(32) NOT NULL,
    primary_view VARCHAR(16) NOT NULL,
    views_csv VARCHAR(64) NOT NULL,
    svg_element_ids_csv VARCHAR(255) NOT NULL,
    display_order INT NOT NULL
);

INSERT INTO muscle_groups (id, display_name, region, primary_view, views_csv, svg_element_ids_csv, display_order)
SELECT v.id, v.display_name, v.region, v.primary_view, v.views_csv, v.svg_element_ids_csv, v.display_order
FROM (
    SELECT CAST('chest' AS VARCHAR(64)) AS id, CAST('Chest' AS VARCHAR(100)) AS display_name, CAST('UPPER_BODY' AS VARCHAR(32)) AS region, CAST('FRONT' AS VARCHAR(16)) AS primary_view, CAST('FRONT' AS VARCHAR(64)) AS views_csv, CAST('chest_left,chest_right' AS VARCHAR(255)) AS svg_element_ids_csv, 1 AS display_order
    UNION ALL SELECT 'front_deltoids', 'Front Deltoids', 'UPPER_BODY', 'FRONT', 'FRONT', 'front_deltoids_left,front_deltoids_right', 2
    UNION ALL SELECT 'lateral_deltoids', 'Lateral Deltoids', 'UPPER_BODY', 'FRONT', 'FRONT,BACK', 'lateral_deltoids_left,lateral_deltoids_right', 3
    UNION ALL SELECT 'rear_deltoids', 'Rear Deltoids', 'UPPER_BODY', 'BACK', 'BACK', 'rear_deltoids_left,rear_deltoids_right', 4
    UNION ALL SELECT 'biceps', 'Biceps', 'UPPER_BODY', 'FRONT', 'FRONT', 'biceps_left,biceps_right', 5
    UNION ALL SELECT 'triceps', 'Triceps', 'UPPER_BODY', 'BACK', 'BACK', 'triceps_left,triceps_right', 6
    UNION ALL SELECT 'forearms', 'Forearms', 'UPPER_BODY', 'FRONT', 'FRONT,BACK', 'forearms_left,forearms_right', 7
    UNION ALL SELECT 'upper_trapezius', 'Upper Trapezius', 'UPPER_BODY', 'BACK', 'FRONT,BACK', 'upper_trapezius_left,upper_trapezius_right', 8
    UNION ALL SELECT 'middle_trapezius', 'Middle Trapezius', 'UPPER_BODY', 'BACK', 'BACK', 'middle_trapezius_left,middle_trapezius_right', 9
    UNION ALL SELECT 'lower_trapezius', 'Lower Trapezius', 'UPPER_BODY', 'BACK', 'BACK', 'lower_trapezius_left,lower_trapezius_right', 10
    UNION ALL SELECT 'latissimus_dorsi', 'Latissimus Dorsi', 'UPPER_BODY', 'BACK', 'FRONT,BACK', 'latissimus_dorsi_left,latissimus_dorsi_right', 11
    UNION ALL SELECT 'teres_major', 'Teres Major', 'UPPER_BODY', 'BACK', 'BACK', 'teres_major_left,teres_major_right', 12
    UNION ALL SELECT 'rhomboids', 'Rhomboids', 'UPPER_BODY', 'BACK', 'BACK', 'rhomboids_left,rhomboids_right', 13
    UNION ALL SELECT 'erector_spinae', 'Erector Spinae', 'CORE', 'BACK', 'BACK', 'erector_spinae_left,erector_spinae_right', 14
    UNION ALL SELECT 'rectus_abdominis', 'Rectus Abdominis', 'CORE', 'FRONT', 'FRONT', 'rectus_abdominis_left,rectus_abdominis_right', 15
    UNION ALL SELECT 'obliques', 'Obliques', 'CORE', 'FRONT', 'FRONT', 'obliques_left,obliques_right', 16
    UNION ALL SELECT 'transverse_abdominis', 'Transverse Abdominis', 'CORE', 'FRONT', 'FRONT', 'transverse_abdominis_left,transverse_abdominis_right', 17
    UNION ALL SELECT 'glutes', 'Glutes', 'LOWER_BODY', 'BACK', 'BACK', 'glutes_left,glutes_right', 18
    UNION ALL SELECT 'quadriceps', 'Quadriceps', 'LOWER_BODY', 'FRONT', 'FRONT', 'quadriceps_left,quadriceps_right', 19
    UNION ALL SELECT 'hamstrings', 'Hamstrings', 'LOWER_BODY', 'BACK', 'BACK', 'hamstrings_left,hamstrings_right', 20
    UNION ALL SELECT 'adductors', 'Hip Adductors', 'LOWER_BODY', 'FRONT', 'FRONT', 'adductors_left,adductors_right', 21
    UNION ALL SELECT 'calves', 'Calves', 'LOWER_BODY', 'BACK', 'FRONT,BACK', 'calves_left,calves_right', 22
) v
WHERE NOT EXISTS (
    SELECT 1 FROM muscle_groups mg WHERE mg.id = v.id
);

CREATE TABLE IF NOT EXISTS exercise_muscle_targets (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    muscle_id VARCHAR(64) NOT NULL REFERENCES muscle_groups(id) ON DELETE RESTRICT,
    role VARCHAR(16) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exercise_muscle_target UNIQUE (exercise_id, muscle_id),
    CONSTRAINT chk_exercise_muscle_role CHECK (role IN ('PRIMARY', 'SECONDARY'))
);

CREATE INDEX IF NOT EXISTS idx_exercise_muscle_targets_exercise
    ON exercise_muscle_targets(exercise_id);

CREATE INDEX IF NOT EXISTS idx_exercise_muscle_targets_muscle
    ON exercise_muscle_targets(muscle_id);

-- Seed canonical primary & secondary targets for standard calisthenics exercises
INSERT INTO exercise_muscle_targets (exercise_id, muscle_id, role, display_order)
SELECT e.id, s.muscle_id, s.role, s.display_order
FROM exercises e
JOIN (
    -- PUSH_UP
    SELECT CAST('PUSH_UP' AS VARCHAR(50)) AS code, CAST('chest' AS VARCHAR(64)) AS muscle_id, CAST('PRIMARY' AS VARCHAR(16)) AS role, 0 AS display_order
    UNION ALL SELECT 'PUSH_UP', 'front_deltoids', 'SECONDARY', 1
    UNION ALL SELECT 'PUSH_UP', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'PUSH_UP', 'rectus_abdominis', 'SECONDARY', 3
    UNION ALL SELECT 'PUSH_UP', 'transverse_abdominis', 'SECONDARY', 4

    -- DIPS
    UNION ALL SELECT 'DIPS', 'triceps', 'PRIMARY', 0
    UNION ALL SELECT 'DIPS', 'chest', 'PRIMARY', 1
    UNION ALL SELECT 'DIPS', 'front_deltoids', 'SECONDARY', 2

    -- PIKE_PUSH_UP
    UNION ALL SELECT 'PIKE_PUSH_UP', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'PIKE_PUSH_UP', 'lateral_deltoids', 'SECONDARY', 1
    UNION ALL SELECT 'PIKE_PUSH_UP', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'PIKE_PUSH_UP', 'chest', 'SECONDARY', 3
    UNION ALL SELECT 'PIKE_PUSH_UP', 'upper_trapezius', 'SECONDARY', 4
    UNION ALL SELECT 'PIKE_PUSH_UP', 'rectus_abdominis', 'SECONDARY', 5

    -- PULL_UP
    UNION ALL SELECT 'PULL_UP', 'latissimus_dorsi', 'PRIMARY', 0
    UNION ALL SELECT 'PULL_UP', 'teres_major', 'PRIMARY', 1
    UNION ALL SELECT 'PULL_UP', 'biceps', 'SECONDARY', 2
    UNION ALL SELECT 'PULL_UP', 'rhomboids', 'SECONDARY', 3
    UNION ALL SELECT 'PULL_UP', 'middle_trapezius', 'SECONDARY', 4
    UNION ALL SELECT 'PULL_UP', 'lower_trapezius', 'SECONDARY', 5
    UNION ALL SELECT 'PULL_UP', 'forearms', 'SECONDARY', 6
    UNION ALL SELECT 'PULL_UP', 'rectus_abdominis', 'SECONDARY', 7

    -- MUSCLE_UP
    UNION ALL SELECT 'MUSCLE_UP', 'latissimus_dorsi', 'PRIMARY', 0
    UNION ALL SELECT 'MUSCLE_UP', 'triceps', 'PRIMARY', 1
    UNION ALL SELECT 'MUSCLE_UP', 'chest', 'PRIMARY', 2
    UNION ALL SELECT 'MUSCLE_UP', 'teres_major', 'SECONDARY', 3
    UNION ALL SELECT 'MUSCLE_UP', 'biceps', 'SECONDARY', 4
    UNION ALL SELECT 'MUSCLE_UP', 'front_deltoids', 'SECONDARY', 5
    UNION ALL SELECT 'MUSCLE_UP', 'forearms', 'SECONDARY', 6
    UNION ALL SELECT 'MUSCLE_UP', 'rectus_abdominis', 'SECONDARY', 7

    -- SQUAT
    UNION ALL SELECT 'SQUAT', 'quadriceps', 'PRIMARY', 0
    UNION ALL SELECT 'SQUAT', 'glutes', 'PRIMARY', 1
    UNION ALL SELECT 'SQUAT', 'hamstrings', 'SECONDARY', 2
    UNION ALL SELECT 'SQUAT', 'adductors', 'SECONDARY', 3
    UNION ALL SELECT 'SQUAT', 'calves', 'SECONDARY', 4
    UNION ALL SELECT 'SQUAT', 'erector_spinae', 'SECONDARY', 5
    UNION ALL SELECT 'SQUAT', 'rectus_abdominis', 'SECONDARY', 6

    -- L_SIT
    UNION ALL SELECT 'L_SIT', 'rectus_abdominis', 'PRIMARY', 0
    UNION ALL SELECT 'L_SIT', 'transverse_abdominis', 'PRIMARY', 1
    UNION ALL SELECT 'L_SIT', 'obliques', 'SECONDARY', 2
    UNION ALL SELECT 'L_SIT', 'quadriceps', 'SECONDARY', 3
    UNION ALL SELECT 'L_SIT', 'triceps', 'SECONDARY', 4
    UNION ALL SELECT 'L_SIT', 'latissimus_dorsi', 'SECONDARY', 5
    UNION ALL SELECT 'L_SIT', 'forearms', 'SECONDARY', 6

    -- HANDSTAND
    UNION ALL SELECT 'HANDSTAND', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'HANDSTAND', 'upper_trapezius', 'PRIMARY', 1
    UNION ALL SELECT 'HANDSTAND', 'lateral_deltoids', 'SECONDARY', 2
    UNION ALL SELECT 'HANDSTAND', 'triceps', 'SECONDARY', 3
    UNION ALL SELECT 'HANDSTAND', 'forearms', 'SECONDARY', 4
    UNION ALL SELECT 'HANDSTAND', 'rectus_abdominis', 'SECONDARY', 5
    UNION ALL SELECT 'HANDSTAND', 'transverse_abdominis', 'SECONDARY', 6
    UNION ALL SELECT 'HANDSTAND', 'erector_spinae', 'SECONDARY', 7

    -- TUCK_PLANCHE
    UNION ALL SELECT 'TUCK_PLANCHE', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'TUCK_PLANCHE', 'chest', 'SECONDARY', 1
    UNION ALL SELECT 'TUCK_PLANCHE', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'TUCK_PLANCHE', 'forearms', 'SECONDARY', 3
    UNION ALL SELECT 'TUCK_PLANCHE', 'rectus_abdominis', 'SECONDARY', 4
    UNION ALL SELECT 'TUCK_PLANCHE', 'transverse_abdominis', 'SECONDARY', 5

    -- ADV_TUCK_PLANCHE
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'chest', 'SECONDARY', 1
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'forearms', 'SECONDARY', 3
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'erector_spinae', 'SECONDARY', 4
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'rectus_abdominis', 'SECONDARY', 5
    UNION ALL SELECT 'ADV_TUCK_PLANCHE', 'transverse_abdominis', 'SECONDARY', 6

    -- STRADDLE_PLANCHE
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'chest', 'SECONDARY', 1
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'forearms', 'SECONDARY', 3
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'glutes', 'SECONDARY', 4
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'erector_spinae', 'SECONDARY', 5
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'rectus_abdominis', 'SECONDARY', 6
    UNION ALL SELECT 'STRADDLE_PLANCHE', 'transverse_abdominis', 'SECONDARY', 7

    -- FULL_PLANCHE
    UNION ALL SELECT 'FULL_PLANCHE', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'FULL_PLANCHE', 'chest', 'SECONDARY', 1
    UNION ALL SELECT 'FULL_PLANCHE', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'FULL_PLANCHE', 'forearms', 'SECONDARY', 3
    UNION ALL SELECT 'FULL_PLANCHE', 'glutes', 'SECONDARY', 4
    UNION ALL SELECT 'FULL_PLANCHE', 'erector_spinae', 'SECONDARY', 5
    UNION ALL SELECT 'FULL_PLANCHE', 'rectus_abdominis', 'SECONDARY', 6
    UNION ALL SELECT 'FULL_PLANCHE', 'transverse_abdominis', 'SECONDARY', 7

    -- PLANCHE
    UNION ALL SELECT 'PLANCHE', 'front_deltoids', 'PRIMARY', 0
    UNION ALL SELECT 'PLANCHE', 'chest', 'SECONDARY', 1
    UNION ALL SELECT 'PLANCHE', 'triceps', 'SECONDARY', 2
    UNION ALL SELECT 'PLANCHE', 'forearms', 'SECONDARY', 3
    UNION ALL SELECT 'PLANCHE', 'erector_spinae', 'SECONDARY', 4
    UNION ALL SELECT 'PLANCHE', 'rectus_abdominis', 'SECONDARY', 5
    UNION ALL SELECT 'PLANCHE', 'transverse_abdominis', 'SECONDARY', 6

    -- FRONT_LEVER
    UNION ALL SELECT 'FRONT_LEVER', 'latissimus_dorsi', 'PRIMARY', 0
    UNION ALL SELECT 'FRONT_LEVER', 'teres_major', 'PRIMARY', 1
    UNION ALL SELECT 'FRONT_LEVER', 'rear_deltoids', 'SECONDARY', 2
    UNION ALL SELECT 'FRONT_LEVER', 'rhomboids', 'SECONDARY', 3
    UNION ALL SELECT 'FRONT_LEVER', 'middle_trapezius', 'SECONDARY', 4
    UNION ALL SELECT 'FRONT_LEVER', 'lower_trapezius', 'SECONDARY', 5
    UNION ALL SELECT 'FRONT_LEVER', 'forearms', 'SECONDARY', 6
    UNION ALL SELECT 'FRONT_LEVER', 'rectus_abdominis', 'SECONDARY', 7
    UNION ALL SELECT 'FRONT_LEVER', 'transverse_abdominis', 'SECONDARY', 8
    UNION ALL SELECT 'FRONT_LEVER', 'erector_spinae', 'SECONDARY', 9

    -- FRONT_LEVER_RAISES
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'latissimus_dorsi', 'PRIMARY', 0
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'teres_major', 'PRIMARY', 1
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'rear_deltoids', 'SECONDARY', 2
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'rhomboids', 'SECONDARY', 3
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'lower_trapezius', 'SECONDARY', 4
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'forearms', 'SECONDARY', 5
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'rectus_abdominis', 'SECONDARY', 6
    UNION ALL SELECT 'FRONT_LEVER_RAISES', 'transverse_abdominis', 'SECONDARY', 7
) s ON UPPER(e.code) = s.code
WHERE NOT EXISTS (
    SELECT 1 FROM exercise_muscle_targets emt
    WHERE emt.exercise_id = e.id AND emt.muscle_id = s.muscle_id
);

-- Safe, idempotent backfill for any other existing exercises matching legacy muscle keywords
INSERT INTO exercise_muscle_targets (exercise_id, muscle_id, role, display_order)
SELECT
    matched.exercise_id,
    matched.muscle_id,
    CASE WHEN matched.is_primary = 1 THEN 'PRIMARY' ELSE 'SECONDARY' END,
    CASE WHEN matched.is_primary = 1 THEN matched.display_order ELSE 100 + matched.display_order END
FROM (
    SELECT
        e.id AS exercise_id,
        m.muscle_id,
        m.display_order,
        CASE
            WHEN m.muscle_id = 'chest' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%chest%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%pectoral%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%pecs%') THEN 1
            WHEN m.muscle_id = 'front_deltoids' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%front delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%anterior delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%front_deltoids%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%shoulder%') THEN 1
            WHEN m.muscle_id = 'lateral_deltoids' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%lateral delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%side delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%medial delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%lateral_deltoids%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%shoulder%') THEN 1
            WHEN m.muscle_id = 'rear_deltoids' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%rear delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%posterior delt%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%rear_deltoids%') THEN 1
            WHEN m.muscle_id = 'biceps' AND LOWER(COALESCE(e.target_muscles, '')) LIKE '%bicep%' THEN 1
            WHEN m.muscle_id = 'triceps' AND LOWER(COALESCE(e.target_muscles, '')) LIKE '%tricep%' THEN 1
            WHEN m.muscle_id = 'forearms' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%forearm%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%grip%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%wrist%') THEN 1
            WHEN m.muscle_id = 'upper_trapezius' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%upper trap%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%upper_trapezius%' OR ((LOWER(COALESCE(e.target_muscles, '')) LIKE '%trapezius%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%traps%') AND LOWER(COALESCE(e.target_muscles, '')) NOT LIKE '%middle trap%' AND LOWER(COALESCE(e.target_muscles, '')) NOT LIKE '%mid trap%' AND LOWER(COALESCE(e.target_muscles, '')) NOT LIKE '%lower trap%')) THEN 1
            WHEN m.muscle_id = 'middle_trapezius' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%middle trap%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%mid trap%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%middle_trapezius%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%upper back%') THEN 1
            WHEN m.muscle_id = 'lower_trapezius' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%lower trap%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%lower_trapezius%') THEN 1
            WHEN m.muscle_id = 'latissimus_dorsi' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%latissimus%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%lats%') THEN 1
            WHEN m.muscle_id = 'teres_major' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%teres major%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%teres_major%') THEN 1
            WHEN m.muscle_id = 'rhomboids' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%rhomboid%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%upper back%') THEN 1
            WHEN m.muscle_id = 'erector_spinae' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%erector%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%lower back%') THEN 1
            WHEN m.muscle_id = 'rectus_abdominis' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%rectus abdominis%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%rectus_abdominis%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%abdominal%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%abs%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%core%') THEN 1
            WHEN m.muscle_id = 'obliques' AND LOWER(COALESCE(e.target_muscles, '')) LIKE '%oblique%' THEN 1
            WHEN m.muscle_id = 'transverse_abdominis' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%transverse abdominis%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%transverse_abdominis%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%core%') THEN 1
            WHEN m.muscle_id = 'glutes' AND LOWER(COALESCE(e.target_muscles, '')) LIKE '%glute%' THEN 1
            WHEN m.muscle_id = 'quadriceps' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%quadricep%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%quads%') THEN 1
            WHEN m.muscle_id = 'hamstrings' AND LOWER(COALESCE(e.target_muscles, '')) LIKE '%hamstring%' THEN 1
            WHEN m.muscle_id = 'adductors' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%adductor%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%inner thigh%') THEN 1
            WHEN m.muscle_id = 'calves' AND (LOWER(COALESCE(e.target_muscles, '')) LIKE '%calves%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%calf%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%gastrocnemius%' OR LOWER(COALESCE(e.target_muscles, '')) LIKE '%soleus%') THEN 1
            ELSE 0
        END AS is_primary,
        CASE
            WHEN m.muscle_id = 'chest' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%chest%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%pectoral%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%pecs%') THEN 1
            WHEN m.muscle_id = 'front_deltoids' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%front delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%anterior delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%front_deltoids%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%shoulder%') THEN 1
            WHEN m.muscle_id = 'lateral_deltoids' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%lateral delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%side delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%medial delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%lateral_deltoids%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%shoulder%') THEN 1
            WHEN m.muscle_id = 'rear_deltoids' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%rear delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%posterior delt%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%rear_deltoids%') THEN 1
            WHEN m.muscle_id = 'biceps' AND LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%bicep%' THEN 1
            WHEN m.muscle_id = 'triceps' AND LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%tricep%' THEN 1
            WHEN m.muscle_id = 'forearms' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%forearm%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%grip%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%wrist%') THEN 1
            WHEN m.muscle_id = 'upper_trapezius' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%upper trap%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%upper_trapezius%' OR ((LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%trapezius%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%traps%') AND LOWER(COALESCE(e.secondary_muscles, '')) NOT LIKE '%middle trap%' AND LOWER(COALESCE(e.secondary_muscles, '')) NOT LIKE '%mid trap%' AND LOWER(COALESCE(e.secondary_muscles, '')) NOT LIKE '%lower trap%')) THEN 1
            WHEN m.muscle_id = 'middle_trapezius' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%middle trap%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%mid trap%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%middle_trapezius%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%upper back%') THEN 1
            WHEN m.muscle_id = 'lower_trapezius' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%lower trap%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%lower_trapezius%') THEN 1
            WHEN m.muscle_id = 'latissimus_dorsi' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%latissimus%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%lats%') THEN 1
            WHEN m.muscle_id = 'teres_major' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%teres major%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%teres_major%') THEN 1
            WHEN m.muscle_id = 'rhomboids' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%rhomboid%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%upper back%') THEN 1
            WHEN m.muscle_id = 'erector_spinae' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%erector%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%lower back%') THEN 1
            WHEN m.muscle_id = 'rectus_abdominis' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%rectus abdominis%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%rectus_abdominis%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%abdominal%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%abs%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%core%') THEN 1
            WHEN m.muscle_id = 'obliques' AND LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%oblique%' THEN 1
            WHEN m.muscle_id = 'transverse_abdominis' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%transverse abdominis%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%transverse_abdominis%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%core%') THEN 1
            WHEN m.muscle_id = 'glutes' AND LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%glute%' THEN 1
            WHEN m.muscle_id = 'quadriceps' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%quadricep%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%quads%') THEN 1
            WHEN m.muscle_id = 'hamstrings' AND LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%hamstring%' THEN 1
            WHEN m.muscle_id = 'adductors' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%adductor%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%inner thigh%') THEN 1
            WHEN m.muscle_id = 'calves' AND (LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%calves%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%calf%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%gastrocnemius%' OR LOWER(COALESCE(e.secondary_muscles, '')) LIKE '%soleus%') THEN 1
            ELSE 0
        END AS is_secondary
    FROM exercises e
    CROSS JOIN (
        SELECT CAST('chest' AS VARCHAR(64)) AS muscle_id, 1 AS display_order
        UNION ALL SELECT 'front_deltoids', 2
        UNION ALL SELECT 'lateral_deltoids', 3
        UNION ALL SELECT 'rear_deltoids', 4
        UNION ALL SELECT 'biceps', 5
        UNION ALL SELECT 'triceps', 6
        UNION ALL SELECT 'forearms', 7
        UNION ALL SELECT 'upper_trapezius', 8
        UNION ALL SELECT 'middle_trapezius', 9
        UNION ALL SELECT 'lower_trapezius', 10
        UNION ALL SELECT 'latissimus_dorsi', 11
        UNION ALL SELECT 'teres_major', 12
        UNION ALL SELECT 'rhomboids', 13
        UNION ALL SELECT 'erector_spinae', 14
        UNION ALL SELECT 'rectus_abdominis', 15
        UNION ALL SELECT 'obliques', 16
        UNION ALL SELECT 'transverse_abdominis', 17
        UNION ALL SELECT 'glutes', 18
        UNION ALL SELECT 'quadriceps', 19
        UNION ALL SELECT 'hamstrings', 20
        UNION ALL SELECT 'adductors', 21
        UNION ALL SELECT 'calves', 22
    ) m
    WHERE NOT EXISTS (
        SELECT 1 FROM exercise_muscle_targets existing WHERE existing.exercise_id = e.id
    )
) matched
WHERE matched.is_primary = 1 OR matched.is_secondary = 1;
