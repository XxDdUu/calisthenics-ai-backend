-- V59: Skill Progression & Catalog taxonomy separating Skills from Exercises,
-- supporting Calisthenics and Gym / Weight Training without duplicating exercises or skills.

CREATE TABLE IF NOT EXISTS skill_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    training_type VARCHAR(32) NOT NULL,
    category_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_skill_categories_type_key UNIQUE (training_type, category_key),
    CONSTRAINT chk_skill_cat_training_type CHECK (training_type IN ('CALISTHENICS', 'GYM'))
);

CREATE TABLE IF NOT EXISTS skills (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    training_type VARCHAR(32) NOT NULL,
    category_key VARCHAR(64) NOT NULL,
    subcategory VARCHAR(120),
    description TEXT,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_skills_type_name UNIQUE (training_type, name),
    CONSTRAINT chk_skills_training_type CHECK (training_type IN ('CALISTHENICS', 'GYM'))
);

CREATE TABLE IF NOT EXISTS skill_category_memberships (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    training_type VARCHAR(32) NOT NULL,
    category_key VARCHAR(64) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_skill_cat_membership UNIQUE (skill_id, training_type, category_key),
    CONSTRAINT chk_skill_mem_training_type CHECK (training_type IN ('CALISTHENICS', 'GYM'))
);

CREATE TABLE IF NOT EXISTS skill_progression_levels (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    level_number INT NOT NULL,
    code VARCHAR(80) NOT NULL,
    title VARCHAR(120) NOT NULL,
    difficulty VARCHAR(32) NOT NULL,
    target_criteria VARCHAR(255),
    description TEXT,
    CONSTRAINT uk_skill_level_num UNIQUE (skill_id, level_number),
    CONSTRAINT uk_skill_level_code UNIQUE (skill_id, code)
);

CREATE TABLE IF NOT EXISTS skill_exercise_links (
    id BIGSERIAL PRIMARY KEY,
    skill_id BIGINT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    progression_level_id BIGINT REFERENCES skill_progression_levels(id) ON DELETE SET NULL,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_skill_exercise_link UNIQUE (skill_id, exercise_id),
    CONSTRAINT chk_skill_ex_relation_type CHECK (relation_type IN ('PRIMARY', 'SUPPORTING', 'PREREQUISITE'))
);

CREATE INDEX IF NOT EXISTS idx_skill_categories_type ON skill_categories(training_type, display_order);
CREATE INDEX IF NOT EXISTS idx_skills_type_category ON skills(training_type, category_key, display_order);
CREATE INDEX IF NOT EXISTS idx_skill_memberships_type_cat ON skill_category_memberships(training_type, category_key, display_order);
CREATE INDEX IF NOT EXISTS idx_skill_levels_skill ON skill_progression_levels(skill_id, level_number);
CREATE INDEX IF NOT EXISTS idx_skill_ex_links_skill ON skill_exercise_links(skill_id, progression_level_id, display_order);
CREATE INDEX IF NOT EXISTS idx_skill_ex_links_exercise ON skill_exercise_links(exercise_id);

-- 1. Seed backend-driven categories for Calisthenics & Gym
INSERT INTO skill_categories (code, training_type, category_key, display_name, description, display_order, active)
SELECT v.code, v.training_type, v.category_key, v.display_name, v.description, v.display_order, TRUE
FROM (
    SELECT CAST('CALISTHENICS_PUSH' AS VARCHAR(64)) AS code, CAST('CALISTHENICS' AS VARCHAR(32)) AS training_type, CAST('PUSH' AS VARCHAR(64)) AS category_key, CAST('Push' AS VARCHAR(100)) AS display_name, CAST('Bodyweight horizontal, vertical, and straight-arm pressing progressions.' AS TEXT) AS description, 1 AS display_order
    UNION ALL SELECT 'CALISTHENICS_PULL', 'CALISTHENICS', 'PULL', 'Pull', 'Bodyweight vertical, horizontal, unilateral, and straight-arm pulling progressions.', 2
    UNION ALL SELECT 'CALISTHENICS_LEGS', 'CALISTHENICS', 'LEGS', 'Legs', 'Unilateral lower-body strength, balance, and posterior-chain bodyweight progressions.', 3
    UNION ALL SELECT 'CALISTHENICS_CORE', 'CALISTHENICS', 'CORE', 'Core', 'Compression, anti-extension, and straight-arm core strength progressions.', 4
    UNION ALL SELECT 'CALISTHENICS_SKILLS', 'CALISTHENICS', 'SKILLS', 'Skills', 'Signature bodyweight mastery holds and dynamic bar/ring skill transitions.', 5
    UNION ALL SELECT 'CALISTHENICS_MOBILITY', 'CALISTHENICS', 'MOBILITY', 'Mobility', 'Joint preparation, overhead mobility, and full-split flexibility progressions.', 6
    UNION ALL SELECT 'GYM_PUSH', 'GYM', 'PUSH', 'Push', 'Barbell, dumbbell, and machine chest and overhead pressing progressions.', 1
    UNION ALL SELECT 'GYM_PULL', 'GYM', 'PULL', 'Pull', 'Free-weight rows, cable pulldowns, and weighted vertical pulling progressions.', 2
    UNION ALL SELECT 'GYM_LEGS', 'GYM', 'LEGS', 'Legs', 'Barbell squats, hip hinges, leg machines, and targeted lower-body hypertrophy.', 3
    UNION ALL SELECT 'GYM_CORE', 'GYM', 'CORE', 'Core', 'Loaded trunk flexion, anti-rotation, and core bracing movements.', 4
    UNION ALL SELECT 'GYM_SHOULDERS_AND_ARMS', 'GYM', 'SHOULDERS_AND_ARMS', 'Shoulders & Arms', 'Targeted deltoid, biceps, triceps, and rotator cuff isolation progressions.', 5
    UNION ALL SELECT 'GYM_MOBILITY', 'GYM', 'MOBILITY', 'Mobility', 'Loaded mobility and joint prep supporting squat, hinge, and overhead positions.', 6
) v
WHERE NOT EXISTS (
    SELECT 1 FROM skill_categories sc WHERE sc.code = v.code OR (sc.training_type = v.training_type AND sc.category_key = v.category_key)
);

-- 2. Seed canonical Skills (strictly separated from Exercises; no duplicate skills)
INSERT INTO skills (code, name, training_type, category_key, subcategory, description, display_order, active, created_at)
SELECT v.code, v.name, v.training_type, v.category_key, v.subcategory, v.description, v.display_order, TRUE, CURRENT_TIMESTAMP
FROM (
    -- Calisthenics Push
    SELECT CAST('CAL_PLANCHE' AS VARCHAR(64)) AS code, CAST('Planche' AS VARCHAR(120)) AS name, CAST('CALISTHENICS' AS VARCHAR(32)) AS training_type, CAST('PUSH' AS VARCHAR(64)) AS category_key, CAST('Straight-Arm Pushing' AS VARCHAR(120)) AS subcategory, CAST('Horizontal straight-arm balance hold requiring anterior deltoid, scapular protraction, and core tension.' AS TEXT) AS description, 1 AS display_order
    UNION ALL SELECT 'CAL_HSPU', 'Handstand Push Up (HSPU)', 'CALISTHENICS', 'PUSH', 'Vertical Pressing', 'Inverted overhead pressing progression from pike push-ups to freestanding handstand push-ups.', 2
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'Push-up Progressions', 'CALISTHENICS', 'PUSH', 'Horizontal Pressing', 'Progressive horizontal pressing from incline and standard push-ups to archer and one-arm variations.', 3
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'Dip Progressions', 'CALISTHENICS', 'PUSH', 'Vertical & Bar Pressing', 'Parallel bar, straight bar, ring, and weighted dip strength progressions.', 4

    -- Calisthenics Pull
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'Front Lever', 'CALISTHENICS', 'PULL', 'Straight-Arm Pulling', 'Horizontal supine bar hang requiring straight-arm lat depression, scapular strength, and core tension.', 1
    UNION ALL SELECT 'CAL_BACK_LEVER', 'Back Lever', 'CALISTHENICS', 'PULL', 'Shoulder Extension Lever', 'Horizontal face-down bar/ring hold progressing from German hang and Skin the Cat to full lever.', 2
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'One Arm Pull-up', 'CALISTHENICS', 'PULL', 'Unilateral Vertical Pulling', 'Unilateral vertical pulling path from strict pull-ups through archer and assisted variations to strict OAP.', 3
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'Muscle-up', 'CALISTHENICS', 'PULL', 'Explosive Bar & Ring Transition', 'Dynamic pull-to-dip transition skill on pull-up bar or gymnastic rings.', 4

    -- Calisthenics Legs
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'Pistol Squat', 'CALISTHENICS', 'LEGS', 'Unilateral Squat', 'Full-depth single-leg squat progression with free leg extended forward.', 1
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 'Shrimp Squat', 'CALISTHENICS', 'LEGS', 'Quad-Dominant Single-Leg', 'Single-leg knee-dominant squat progression holding the trailing foot behind the hip.', 2
    UNION ALL SELECT 'CAL_NORDIC_CURL', 'Nordic Curl', 'CALISTHENICS', 'LEGS', 'Posterior Chain & Knee Flexion', 'High-tension bodyweight hamstring curl progression from bridges and sliders to full concentric Nordics.', 3

    -- Calisthenics Core
    UNION ALL SELECT 'CAL_L_SIT', 'L-sit', 'CALISTHENICS', 'CORE', 'Isometric Compression', 'Straight-arm support hold with locked legs held horizontal at 90 degrees.', 1
    UNION ALL SELECT 'CAL_V_SIT', 'V-sit', 'CALISTHENICS', 'CORE', 'Advanced Pike Compression', 'High pike compression hold lifting legs above horizontal with forward shoulder lean.', 2
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 'Dragon Flag', 'CALISTHENICS', 'CORE', 'Anti-Extension Body Lever', 'Full-body anti-extension lever anchored on upper back and shoulders.', 3
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 'Front Lever Core Strength', 'CALISTHENICS', 'CORE', 'Straight-Arm Anti-Extension Core', 'Core compression and straight-arm body-line drills that directly transfer to Front Lever.', 4

    -- Calisthenics Skills (note: CAL_MUSCLE_UP also links to SKILLS via skill_category_memberships)
    UNION ALL SELECT 'CAL_HANDSTAND', 'Handstand', 'CALISTHENICS', 'SKILLS', 'Inversion & Balance', 'Vertical overhead balance progression from chest-to-wall alignment to freestanding control.', 1
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 'Human Flag', 'CALISTHENICS', 'SKILLS', 'Lateral Push-Pull Hold', 'Lateral horizontal hold on vertical pole or stall bars combining push, pull, and oblique tension.', 2

    -- Calisthenics Mobility
    UNION ALL SELECT 'CAL_SHOULDER_MOBILITY', 'Shoulder Mobility', 'CALISTHENICS', 'MOBILITY', 'Overhead & Extension ROM', 'Thoracic extension, overhead flexion, and shoulder extension mobility for handstands and levers.', 1
    UNION ALL SELECT 'CAL_WRIST_MOBILITY', 'Wrist Mobility', 'CALISTHENICS', 'MOBILITY', 'Planche & Handstand Joint Prep', 'Wrist extension, flexion, and tendon conditioning for straight-arm skills and inversion.', 2
    UNION ALL SELECT 'CAL_HIP_MOBILITY', 'Hip Mobility', 'CALISTHENICS', 'MOBILITY', 'Squat & Compression ROM', 'Hip flexor, rotator, and ankle dorsiflexion mobility for deep squats and pike compression.', 3
    UNION ALL SELECT 'CAL_MIDDLE_SPLIT', 'Middle Split', 'CALISTHENICS', 'MOBILITY', 'Adductor & Straddle Flexibility', 'Straddle pancake and lateral adductor mobility supporting straddle planche, lever, and splits.', 4
    UNION ALL SELECT 'CAL_FRONT_SPLIT', 'Front Split', 'CALISTHENICS', 'MOBILITY', 'Hamstring & Hip Flexor Flexibility', 'Pike hamstring length and split flexibility for compression and full front splits.', 5

    -- Gym Push
    UNION ALL SELECT 'GYM_BENCH_PRESS', 'Bench Press', 'GYM', 'PUSH', 'Horizontal Compound Press', 'Horizontal chest, front deltoid, and triceps pressing progression on flat bench.', 1
    UNION ALL SELECT 'GYM_INCLINE_BENCH_PRESS', 'Incline Bench Press', 'GYM', 'PUSH', 'Upper Chest Compound Press', 'Incline barbell, dumbbell, and machine pressing targeting clavicular pectorals.', 2
    UNION ALL SELECT 'GYM_OVERHEAD_PRESS', 'Overhead Press', 'GYM', 'PUSH', 'Vertical Barbell & Dumbbell Press', 'Standing and seated overhead pressing progressions for shoulders and triceps.', 3
    UNION ALL SELECT 'GYM_MACHINE_CHEST_PRESS', 'Machine Chest Press', 'GYM', 'PUSH', 'Guided Chest Pressing & Fly', 'Machine and cable pressing and fly variations for stable pectoral hypertrophy.', 4

    -- Gym Pull
    UNION ALL SELECT 'GYM_BARBELL_ROW', 'Barbell Row', 'GYM', 'PULL', 'Horizontal Free-Weight Row', 'Free-weight horizontal rowing progressions building upper-back thickness and lat strength.', 1
    UNION ALL SELECT 'GYM_LAT_PULLDOWN', 'Lat Pulldown', 'GYM', 'PULL', 'Vertical Cable Pulling', 'Cable vertical pulling progressions targeting latissimus dorsi width and scapular control.', 2
    UNION ALL SELECT 'GYM_SEATED_CABLE_ROW', 'Seated Cable Row', 'GYM', 'PULL', 'Horizontal Cable & Machine Row', 'Cable and chest-supported rowing progressions for mid-back and rhomboid development.', 3
    UNION ALL SELECT 'GYM_WEIGHTED_PULL_UP', 'Weighted Pull-up', 'GYM', 'PULL', 'Loaded Vertical Pulling', 'Progressive vertical pulling from assisted pull-up machine to weighted pull-ups and chin-ups.', 4

    -- Gym Legs
    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 'Barbell Squat', 'GYM', 'LEGS', 'Knee-Dominant Compound', 'Loaded squat progressions from goblet and Smith squats to back, front, and pause squats.', 1
    UNION ALL SELECT 'GYM_ROMANIAN_DEADLIFT', 'Romanian Deadlift', 'GYM', 'LEGS', 'Hip Hinge & Posterior Chain', 'Loaded hip-hinge progressions targeting hamstrings, glutes, and spinal erectors.', 2
    UNION ALL SELECT 'GYM_LEG_PRESS', 'Leg Press', 'GYM', 'LEGS', 'Machine Compound Lower Body', 'Sled leg press, hack squat, and pendulum squat progressions for high-output quad work.', 3
    UNION ALL SELECT 'GYM_LEG_EXTENSION', 'Leg Extension', 'GYM', 'LEGS', 'Quadriceps Isolation', 'Bilateral and unilateral knee extension machine progressions.', 4
    UNION ALL SELECT 'GYM_LEG_CURL', 'Leg Curl', 'GYM', 'LEGS', 'Hamstrings Isolation', 'Seated, lying, standing, and GHD knee-flexion hamstring progressions.', 5
    UNION ALL SELECT 'GYM_CALF_RAISE', 'Calf Raise', 'GYM', 'LEGS', 'Ankle Plantarflexion', 'Seated, standing, and sled calf raise progressions targeting gastrocnemius and soleus.', 6

    -- Gym Core
    UNION ALL SELECT 'GYM_CABLE_CRUNCH', 'Cable Crunch', 'GYM', 'CORE', 'Loaded Spinal Flexion', 'Cable crunch, weighted decline crunch, and Pallof anti-rotation core progressions.', 1
    UNION ALL SELECT 'GYM_HANGING_LEG_RAISE', 'Hanging Leg Raise', 'GYM', 'CORE', 'Loaded Hip & Trunk Flexion', 'Weighted captain-chair and strict hanging bar leg raises.', 2
    UNION ALL SELECT 'GYM_AB_WHEEL', 'Ab Wheel', 'GYM', 'CORE', 'Loaded Anti-Extension', 'Ab wheel rollout and weighted anti-extension plank progressions.', 3

    -- Gym Shoulders & Arms
    UNION ALL SELECT 'GYM_LATERAL_RAISE', 'Lateral Raise', 'GYM', 'SHOULDERS_AND_ARMS', 'Deltoid Isolation', 'Dumbbell, cable, and machine lateral and rear deltoid isolation progressions.', 1
    UNION ALL SELECT 'GYM_BICEP_CURL', 'Bicep Curl', 'GYM', 'SHOULDERS_AND_ARMS', 'Elbow Flexor Hypertrophy', 'Barbell, dumbbell, preacher, incline, and cable curl progressions.', 2
    UNION ALL SELECT 'GYM_TRICEPS_EXTENSION', 'Triceps Extension', 'GYM', 'SHOULDERS_AND_ARMS', 'Elbow Extensor Hypertrophy', 'Cable pushdowns, overhead extensions, and skull crusher progressions.', 3
    UNION ALL SELECT 'GYM_FACE_PULL', 'Face Pull', 'GYM', 'SHOULDERS_AND_ARMS', 'Rotator Cuff & Upper Back Health', 'Cable face pulls, external rotations, and Y-raises for scapular and shoulder health.', 4

    -- Gym Mobility
    UNION ALL SELECT 'GYM_SHOULDER_MOBILITY', 'Shoulder & Thoracic Mobility', 'GYM', 'MOBILITY', 'Overhead & Bench Joint Prep', 'Kettlebell halos, dumbbell thoracic pullovers, and banded shoulder mobility.', 1
    UNION ALL SELECT 'GYM_HIP_ANKLE_MOBILITY', 'Hip & Ankle Mobility', 'GYM', 'MOBILITY', 'Squat & Hinge Depth Prep', 'Banded ankle distraction, goblet cossack squats, and loaded Jefferson curls.', 2
) v
WHERE NOT EXISTS (
    SELECT 1 FROM skills s WHERE s.code = v.code OR (s.training_type = v.training_type AND LOWER(s.name) = LOWER(v.name))
);

-- 3. Populate skill_category_memberships (enabling multi-category placement like Muscle-up under Pull & Skills)
INSERT INTO skill_category_memberships (skill_id, training_type, category_key, is_primary, display_order)
SELECT s.id, s.training_type, s.category_key, TRUE, s.display_order
FROM skills s
WHERE NOT EXISTS (
    SELECT 1 FROM skill_category_memberships m
    WHERE m.skill_id = s.id AND m.training_type = s.training_type AND m.category_key = s.category_key
);

INSERT INTO skill_category_memberships (skill_id, training_type, category_key, is_primary, display_order)
SELECT s.id, 'CALISTHENICS', 'SKILLS', FALSE, 3
FROM skills s
WHERE s.code = 'CAL_MUSCLE_UP'
  AND NOT EXISTS (
      SELECT 1 FROM skill_category_memberships m
      WHERE m.skill_id = s.id AND m.training_type = 'CALISTHENICS' AND m.category_key = 'SKILLS'
  );

-- 4. Seed structured Skill Progression Levels (hierarchical roadmap for every skill)
INSERT INTO skill_progression_levels (skill_id, level_number, code, title, difficulty, target_criteria, description)
SELECT s.id, v.level_number, v.code, v.title, v.difficulty, v.target_criteria, v.description
FROM (
    -- Planche
    SELECT CAST('CAL_PLANCHE' AS VARCHAR(64)) AS skill_code, 1 AS level_number, CAST('PLANCHE_L1' AS VARCHAR(80)) AS code, CAST('Level 1 · Foundation Lean' AS VARCHAR(120)) AS title, CAST('BEGINNER' AS VARCHAR(32)) AS difficulty, CAST('4 x 20-30s locked-arm lean' AS VARCHAR(255)) AS target_criteria, CAST('Condition wrists, biceps tendons, and scapular protraction.' AS TEXT) AS description
    UNION ALL SELECT 'CAL_PLANCHE', 2, 'PLANCHE_L2', 'Level 2 · Assisted Balance', 'BEGINNER', '4 x 20-30s Frog Stand / Crow', 'Learn forward weight transfer and finger-pressure balance.'
    UNION ALL SELECT 'CAL_PLANCHE', 3, 'PLANCHE_L3', 'Level 3 · Tuck Planche', 'INTERMEDIATE', '5 x 12-20s clean tuck hold', 'Unassisted straight-arm isometric hold with hips at shoulder height.'
    UNION ALL SELECT 'CAL_PLANCHE', 4, 'PLANCHE_L4', 'Level 4 · Advanced Tuck Planche', 'ADVANCED', '5 x 10-15s flat-back hold', 'Extend hips backward to 90 degrees with protracted scapula.'
    UNION ALL SELECT 'CAL_PLANCHE', 5, 'PLANCHE_L5', 'Level 5 · Straddle & Full Planche', 'ELITE', '5 x 5-12s horizontal hold', 'Full-extension horizontal straight-body planche mastery.'

    -- HSPU
    UNION ALL SELECT 'CAL_HSPU', 1, 'HSPU_L1', 'Level 1 · Pike Press Foundation', 'BEGINNER', '4 x 8-12 strict reps', 'Build overhead pressing mechanics in inverted pike posture.'
    UNION ALL SELECT 'CAL_HSPU', 2, 'HSPU_L2', 'Level 2 · Elevated Pike Press', 'INTERMEDIATE', '4 x 6-10 box-elevated reps', 'Increase torso verticality and shoulder load with elevated feet.'
    UNION ALL SELECT 'CAL_HSPU', 3, 'HSPU_L3', 'Level 3 · Wall Handstand Push-Up', 'ADVANCED', '4 x 4-8 full ROM reps', 'Full bodyweight vertical pressing against a wall.'
    UNION ALL SELECT 'CAL_HSPU', 4, 'HSPU_L4', 'Level 4 · Freestanding HSPU', 'ELITE', '4 x 2-5 freestanding reps', 'Balanced vertical pressing without wall support.'

    -- Push-up Progressions
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 1, 'PUSHUP_L1', 'Level 1 · Foundational Push-Up', 'BEGINNER', '3-4 x 12-15 clean reps', 'Incline, scapular, and standard push-ups with rigid plank line.'
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 2, 'PUSHUP_L2', 'Level 2 · Leverage & Deficit', 'INTERMEDIATE', '4 x 8-12 reps', 'Diamond, decline, ring, and deep deficit push-ups.'
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 3, 'PUSHUP_L3', 'Level 3 · Unilateral & Forward Lean', 'ADVANCED', '4 x 5-8 reps', 'Archer push-ups, pseudo planche push-ups, and explosive variations.'
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 4, 'PUSHUP_L4', 'Level 4 · One-Arm Push-Up', 'ELITE', '4 x 3-6 strict reps/arm', 'Strict unilateral horizontal pressing with anti-rotation core.'

    -- Dip Progressions
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 1, 'DIP_L1', 'Level 1 · Support & Basic Dip', 'BEGINNER', '3-4 x 8-12 reps', 'Straight-arm ring support and parallel bar dips with shoulder depression.'
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 2, 'DIP_L2', 'Level 2 · Bar & Deficit Dips', 'INTERMEDIATE', '4 x 8-12 strict reps', 'Straight bar dips and full-depth parallel bar dips.'
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 3, 'DIP_L3', 'Level 3 · Ring, Korean & Weighted Dips', 'ADVANCED', '4 x 5-10 reps', 'Unstable gymnastic ring dips, Korean dips, and weighted vest dips.'

    -- Front Lever
    UNION ALL SELECT 'CAL_FRONT_LEVER', 1, 'FL_L1', 'Level 1 · Scapular & Tuck Hold', 'BEGINNER', '4 x 15-25s tuck hold', 'Develop active scapular depression and entry tucked horizontal hold.'
    UNION ALL SELECT 'CAL_FRONT_LEVER', 2, 'FL_L2', 'Level 2 · Advanced Tuck & Dynamic', 'INTERMEDIATE', '4 x 10-20s / 4 x 5-8 raises', 'Flat-back advanced tuck hold, tuck rows, and front lever raises.'
    UNION ALL SELECT 'CAL_FRONT_LEVER', 3, 'FL_L3', 'Level 3 · One-Leg & Negatives', 'ADVANCED', '4 x 8-15s per side', 'Asymmetric hip extension and controlled straight-body negatives.'
    UNION ALL SELECT 'CAL_FRONT_LEVER', 4, 'FL_L4', 'Level 4 · Straddle & Full Front Lever', 'ELITE', '4 x 5-12s horizontal hold', 'Full straight-arm horizontal front lever and straddle lever rows.'

    -- Back Lever
    UNION ALL SELECT 'CAL_BACK_LEVER', 1, 'BL_L1', 'Level 1 · German Hang & Skin the Cat', 'BEGINNER', '3 x 20-30s / 3-5 rotations', 'Prepare biceps tendons and shoulder extension mobility safely.'
    UNION ALL SELECT 'CAL_BACK_LEVER', 2, 'BL_L2', 'Level 2 · Tuck Back Lever', 'INTERMEDIATE', '4 x 15-25s hold', 'First horizontal face-down hold with knees tucked.'
    UNION ALL SELECT 'CAL_BACK_LEVER', 3, 'BL_L3', 'Level 3 · Straddle Back Lever', 'ADVANCED', '4 x 10-15s hold', 'Extended horizontal back lever with wide straddle.'
    UNION ALL SELECT 'CAL_BACK_LEVER', 4, 'BL_L4', 'Level 4 · Full Back Lever', 'ADVANCED', '4 x 8-15s full hold', 'Complete horizontal straight-body back lever.'

    -- One Arm Pull-up
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 1, 'OAP_L1', 'Level 1 · Strict Pull-Up Base', 'BEGINNER', '4 x 8-12 chest-to-bar reps', 'Build strict vertical pulling and scapular control.'
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 2, 'OAP_L2', 'Level 2 · Weighted & Asymmetric', 'INTERMEDIATE', '4 x 5-8 reps', 'Weighted chin-ups/pull-ups and commando pull-ups.'
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 3, 'OAP_L3', 'Level 3 · Archer & Assisted Unilateral', 'ADVANCED', '4 x 3-6 reps/side', 'Archer, typewriter, mantle chin-ups, and one-arm hang pulls.'
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 4, 'OAP_L4', 'Level 4 · Strict One-Arm Pull-Up', 'ELITE', '4 x 1-3 reps/arm', 'Unassisted strict single-arm vertical pull from dead hang.'

    -- Muscle-up
    UNION ALL SELECT 'CAL_MUSCLE_UP', 1, 'MU_L1', 'Level 1 · Pull & Dip Foundation', 'BEGINNER', '10 pull-ups + 12 dips', 'Build requisite vertical pull and bar dip capacity.'
    UNION ALL SELECT 'CAL_MUSCLE_UP', 2, 'MU_L2', 'Level 2 · Explosive Pull & Transition', 'INTERMEDIATE', '4 x 5-8 explosive reps', 'False-grip pull-ups, high pull-ups, straight bar dips, and transition drills.'
    UNION ALL SELECT 'CAL_MUSCLE_UP', 3, 'MU_L3', 'Level 3 · Strict Bar & Ring Muscle-Up', 'ADVANCED', '4 x 2-5 strict reps', 'Controlled bar and ring muscle-up without excessive kip.'

    -- Pistol Squat
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 1, 'PISTOL_L1', 'Level 1 · Squat & Split Base', 'BEGINNER', '3-4 x 10-15 reps', 'Bodyweight squats, split squats, and Bulgarian split squats.'
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 2, 'PISTOL_L2', 'Level 2 · Assisted & Box Pistol', 'INTERMEDIATE', '4 x 6-10 reps/leg', 'Counterbalanced, pole-assisted, and box-controlled pistol squats.'
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 3, 'PISTOL_L3', 'Level 3 · Full Floor Pistol Squat', 'ADVANCED', '4 x 4-8 reps/leg', 'Unassisted full-depth single-leg pistol squat.'
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 4, 'PISTOL_L4', 'Level 4 · Elevated Deficit Pistol', 'ELITE', '4 x 3-6 reps/leg', 'Elevated single-leg pistol squat through extended ROM.'

    -- Shrimp Squat
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 1, 'SHRIMP_L1', 'Level 1 · Split & Lunge Base', 'BEGINNER', '3-4 x 10-12 reps/leg', 'Build unilateral quad strength and knee tracking control.'
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 2, 'SHRIMP_L2', 'Level 2 · Assisted & Skater Squat', 'INTERMEDIATE', '3 x 6-10 reps/leg', 'Toe-touch shrimp squat and hand-counterbalanced skater squat.'
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 3, 'SHRIMP_L3', 'Level 3 · Strict Shrimp Squat', 'ADVANCED', '4 x 5-8 reps/leg', 'Touch rear knee softly without toe assistance.'
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 4, 'SHRIMP_L4', 'Level 4 · Double-Hold Shrimp Squat', 'ELITE', '4 x 3-6 reps/leg', 'Two-hand rear foot hold shrimp squat.'

    -- Nordic Curl
    UNION ALL SELECT 'CAL_NORDIC_CURL', 1, 'NORDIC_L1', 'Level 1 · Glute Bridge & Hinge Base', 'BEGINNER', '3-4 x 12-15 reps/leg', 'Single-leg glute bridges and kickstand hinges.'
    UNION ALL SELECT 'CAL_NORDIC_CURL', 2, 'NORDIC_L2', 'Level 2 · Slider & Harop Curl', 'INTERMEDIATE', '3-4 x 8-12 reps', 'Sliding leg curls and hip-flexed razor curls.'
    UNION ALL SELECT 'CAL_NORDIC_CURL', 3, 'NORDIC_L3', 'Level 3 · Eccentric Nordic Negative', 'ADVANCED', '3-4 x 4-6 slow negatives', '5-second controlled lowering with neutral hips.'
    UNION ALL SELECT 'CAL_NORDIC_CURL', 4, 'NORDIC_L4', 'Level 4 · Full Concentric Nordic', 'ELITE', '4 x 3-6 full reps', 'Full eccentric and concentric bodyweight knee flexion.'

    -- L-sit
    UNION ALL SELECT 'CAL_L_SIT', 1, 'LSIT_L1', 'Level 1 · Hollow & Hanging Base', 'BEGINNER', '3 x 30-45s / 10-15 reps', 'Hollow body holds, dead bugs, and hanging knee raises.'
    UNION ALL SELECT 'CAL_L_SIT', 2, 'LSIT_L2', 'Level 2 · Pike Compression & L-Sit', 'INTERMEDIATE', '4 x 15-25s hold', 'Seated pike leg lifts, floor L-sit, and parallettes L-sit.'
    UNION ALL SELECT 'CAL_L_SIT', 3, 'LSIT_L3', 'Level 3 · Dynamic L-Sit Mastery', 'ADVANCED', '4 x 5-8 L-sit pull-ups', 'L-sit flutter kicks and L-sit pull-ups.'

    -- V-sit
    UNION ALL SELECT 'CAL_V_SIT', 1, 'VSIT_L1', 'Level 1 · Active Compression Base', 'INTERMEDIATE', '4 x 12-15 compression lifts', 'L-sit, seated pike/straddle lifts, and V-ups.'
    UNION ALL SELECT 'CAL_V_SIT', 2, 'VSIT_L2', 'Level 2 · High Compression & Toes-to-Bar', 'ADVANCED', '4 x 8-12 strict reps', 'Strict hanging toes-to-bar and high parallettes L-sit.'
    UNION ALL SELECT 'CAL_V_SIT', 3, 'VSIT_L3', 'Level 3 · V-Sit & Manna Prep', 'ELITE', '4 x 8-15s hold', 'Full V-sit hold and high tuck manna progression.'

    -- Dragon Flag
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 1, 'DF_L1', 'Level 1 · Hollow Tension Base', 'BEGINNER', '3 x 30-45s hollow hold', 'Hollow holds, hollow rocks, and reverse crunches.'
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 2, 'DF_L2', 'Level 2 · Hanging Bar Compression', 'INTERMEDIATE', '4 x 8-12 strict reps', 'Strict hanging leg raises and toes-to-bar.'
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 3, 'DF_L3', 'Level 3 · Dragon Flag Negative', 'ADVANCED', '3-4 x 4-6 slow negatives', 'Straight-body eccentric lowering on bench.'
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 4, 'DF_L4', 'Level 4 · Full Strict Dragon Flag', 'ELITE', '4 x 4-6 full reps', 'Full concentric and eccentric straight-body lever.'

    -- Front Lever Core Strength
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 1, 'FLC_L1', 'Level 1 · Hollow & Banded Lat Tension', 'BEGINNER', '3 x 12-15 reps', 'Connect posterior pelvic tilt with straight-arm lat engagement.'
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 2, 'FLC_L2', 'Level 2 · Bar Compression & Raises', 'INTERMEDIATE', '4 x 8-10 reps', 'Front lever raises, toes-to-bar, and hollow-arch control.'
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 3, 'FLC_L3', 'Level 3 · Dynamic Anti-Extension Lever', 'ADVANCED', '3-4 x 6-10 reps', 'Dragon flags and hanging windshield wipers.'

    -- Handstand
    UNION ALL SELECT 'CAL_HANDSTAND', 1, 'HS_L1', 'Level 1 · Wall Line & Bail Safety', 'BEGINNER', '4 x 30-45s chest-to-wall', 'Stack wrists, shoulders, and hips safely with pirouette bail.'
    UNION ALL SELECT 'CAL_HANDSTAND', 2, 'HS_L2', 'Level 2 · Rebalance & Shoulder Taps', 'INTERMEDIATE', '4 x 20-30s / 8-12 taps', 'Wall-back balance pulls and wall shoulder taps.'
    UNION ALL SELECT 'CAL_HANDSTAND', 3, 'HS_L3', 'Level 3 · Freestanding & Press', 'ADVANCED', '5 x 20-45s balance', 'Consistent freestanding holds and tuck press to handstand.'
    UNION ALL SELECT 'CAL_HANDSTAND', 4, 'HS_L4', 'Level 4 · Freestanding HSPU', 'ELITE', '4 x 2-5 reps', 'Full inverted dynamic balance and pressing.'

    -- Human Flag
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 1, 'HF_L1', 'Level 1 · Lateral Core & Support', 'BEGINNER', '3 x 25-35s/side', 'Side plank reach-throughs and Copenhagen side planks.'
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 2, 'HF_L2', 'Level 2 · Vertical Flag Hold', 'INTERMEDIATE', '4 x 15-20s/side', 'Angled push-pull pole support hold.'
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 3, 'HF_L3', 'Level 3 · Tuck Human Flag', 'ADVANCED', '4 x 8-15s/side', 'Horizontal torso hold with knees tucked.'
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 4, 'HF_L4', 'Level 4 · Full Human Flag', 'ELITE', '4 x 5-12s/side', 'Full horizontal straight-body lateral flag.'

    -- Calisthenics Mobility
    UNION ALL SELECT 'CAL_SHOULDER_MOBILITY', 1, 'SM_L1', 'Level 1 · Scapular & Overhead Prep', 'BEGINNER', '3 x 10-15 controlled reps', 'Scapular wall slides, shoulder dislocates, and butcher block stretch.'
    UNION ALL SELECT 'CAL_SHOULDER_MOBILITY', 2, 'SM_L2', 'Level 2 · Thoracic & Extension', 'INTERMEDIATE', '3 x 20-30s / 6-8 reps', 'Thoracic bridge rotations, German hang, and Skin the Cat.'
    UNION ALL SELECT 'CAL_WRIST_MOBILITY', 1, 'WM_L1', 'Level 1 · Wrist Extension & Flexion Prep', 'BEGINNER', '2 x 10-15 pulses/phase', 'Palm pulses, first-knuckle lifts, and dorsal wrist leans.'
    UNION ALL SELECT 'CAL_WRIST_MOBILITY', 2, 'WM_L2', 'Level 2 · Loaded Straight-Arm Conditioning', 'INTERMEDIATE', '3 x 20-30s holds', 'Planche lean and wall handstand wrist tolerance.'
    UNION ALL SELECT 'CAL_HIP_MOBILITY', 1, 'HM_L1', 'Level 1 · Hip Capsule & Ankle Prep', 'BEGINNER', '3 x 10-15 / 45s holds', '90/90 switches, couch stretch, pigeon pose, and ankle dorsiflexion.'
    UNION ALL SELECT 'CAL_HIP_MOBILITY', 2, 'HM_L2', 'Level 2 · Deep Squat & Lateral ROM', 'INTERMEDIATE', '3 x 6-10/side', 'Deep squat sky reach and Cossack squat mobility.'
    UNION ALL SELECT 'CAL_MIDDLE_SPLIT', 1, 'MS_L1', 'Level 1 · Adductor & Straddle Prep', 'BEGINNER', '3 x 10-12 / 45s holds', 'Seated straddle good mornings and pancake folds.'
    UNION ALL SELECT 'CAL_MIDDLE_SPLIT', 2, 'MS_L2', 'Level 2 · Active Frog & Horse Stance', 'INTERMEDIATE', '3 x 45-60s holds', 'Middle split frog slides and isometric horse stance.'
    UNION ALL SELECT 'CAL_FRONT_SPLIT', 1, 'FS_L1', 'Level 1 · Pike Hamstring Length', 'BEGINNER', '3 x 45-60s', 'Seated pike fold and standing pike pulses.'
    UNION ALL SELECT 'CAL_FRONT_SPLIT', 2, 'FS_L2', 'Level 2 · Loaded Posterior & Split Hold', 'INTERMEDIATE', '3 x 6-8 / 45-60s', 'Jefferson curls and front split slider holds.'

    -- Gym Push
    UNION ALL SELECT 'GYM_BENCH_PRESS', 1, 'BP_L1', 'Level 1 · Dumbbell & Floor Control', 'BEGINNER', '3-4 x 8-12 reps', 'Build scapular retraction and bar/dumbbell path stability.'
    UNION ALL SELECT 'GYM_BENCH_PRESS', 2, 'BP_L2', 'Level 2 · Flat Barbell Bench Press', 'INTERMEDIATE', '4 x 5-8 reps', 'Primary horizontal barbell pressing progression.'
    UNION ALL SELECT 'GYM_BENCH_PRESS', 3, 'BP_L3', 'Level 3 · Lockout & Angle Variations', 'ADVANCED', '4 x 6-10 reps', 'Close-grip, decline, and crush press overload variations.'

    UNION ALL SELECT 'GYM_INCLINE_BENCH_PRESS', 1, 'IBP_L1', 'Level 1 · Dumbbell & Smith Incline', 'BEGINNER', '4 x 8-12 reps', 'Controlled 30-45 degree upper pectoral pressing.'
    UNION ALL SELECT 'GYM_INCLINE_BENCH_PRESS', 2, 'IBP_L2', 'Level 2 · Incline Barbell Press', 'INTERMEDIATE', '4 x 6-10 reps', 'Primary upper-chest barbell compound.'
    UNION ALL SELECT 'GYM_INCLINE_BENCH_PRESS', 3, 'IBP_L3', 'Level 3 · Plate-Loaded & Cable Upper Chest', 'ADVANCED', '3-4 x 8-12 reps', 'Hammer strength incline press and incline cable fly.'

    UNION ALL SELECT 'GYM_OVERHEAD_PRESS', 1, 'OHP_L1', 'Level 1 · Seated & Guided Vertical Press', 'BEGINNER', '3-4 x 8-12 reps', 'Seated dumbbell press, machine press, and landmine press.'
    UNION ALL SELECT 'GYM_OVERHEAD_PRESS', 2, 'OHP_L2', 'Level 2 · Standing Barbell OHP', 'INTERMEDIATE', '4 x 5-8 reps', 'Strict standing barbell overhead press and Arnold press.'
    UNION ALL SELECT 'GYM_OVERHEAD_PRESS', 3, 'OHP_L3', 'Level 3 · Strict Z-Press & Push Press', 'ADVANCED', '4 x 4-8 reps', 'Core-demanding Z-press and heavy barbell push press.'

    UNION ALL SELECT 'GYM_MACHINE_CHEST_PRESS', 1, 'MCP_L1', 'Level 1 · Seated Machine Chest Press', 'BEGINNER', '3-4 x 10-14 reps', 'Guided horizontal and neutral-grip machine chest presses.'
    UNION ALL SELECT 'GYM_MACHINE_CHEST_PRESS', 2, 'MCP_L2', 'Level 2 · Cable & Unilateral Press', 'INTERMEDIATE', '3 x 10-14 reps', 'Single-arm machine and seated cable bench presses.'
    UNION ALL SELECT 'GYM_MACHINE_CHEST_PRESS', 3, 'MCP_L3', 'Level 3 · Machine & Cable Isolation', 'INTERMEDIATE', '3-4 x 12-16 reps', 'Pec deck and cable crossover chest flyes.'

    -- Gym Pull
    UNION ALL SELECT 'GYM_BARBELL_ROW', 1, 'BR_L1', 'Level 1 · Supported Dumbbell Row', 'BEGINNER', '3-4 x 8-14 reps', 'Chest-supported dumbbell row and single-arm bench row.'
    UNION ALL SELECT 'GYM_BARBELL_ROW', 2, 'BR_L2', 'Level 2 · Bent-Over Barbell Row', 'INTERMEDIATE', '4 x 8-10 reps', 'Yates and reverse-grip bent-over barbell rows.'
    UNION ALL SELECT 'GYM_BARBELL_ROW', 3, 'BR_L3', 'Level 3 · Pendlay & Seal Barbell Row', 'ADVANCED', '4 x 5-8 reps', 'Strict dead-stop Pendlay rows and chest-supported barbell rows.'

    UNION ALL SELECT 'GYM_LAT_PULLDOWN', 1, 'LP_L1', 'Level 1 · Wide & Neutral Lat Pulldown', 'BEGINNER', '4 x 8-12 reps', 'Bilateral cable vertical pulling with full scapular depression.'
    UNION ALL SELECT 'GYM_LAT_PULLDOWN', 2, 'LP_L2', 'Level 2 · Supinated & Unilateral Pulldown', 'INTERMEDIATE', '3-4 x 10-14 reps', 'Reverse-grip and single-arm kneeling cable pulldowns.'
    UNION ALL SELECT 'GYM_LAT_PULLDOWN', 3, 'LP_L3', 'Level 3 · Straight-Arm Lat Isolation', 'INTERMEDIATE', '3 x 12-16 reps', 'Straight-arm cable lat pulldown for lat activation.'

    UNION ALL SELECT 'GYM_SEATED_CABLE_ROW', 1, 'SCR_L1', 'Level 1 · Chest-Supported Machine Row', 'BEGINNER', '3-4 x 10-14 reps', 'Neutral and wide-grip supported horizontal rows.'
    UNION ALL SELECT 'GYM_SEATED_CABLE_ROW', 2, 'SCR_L2', 'Level 2 · Seated V-Bar Cable Row', 'INTERMEDIATE', '4 x 10-14 reps', 'Strict upright seated cable row and T-bar row.'
    UNION ALL SELECT 'GYM_SEATED_CABLE_ROW', 3, 'SCR_L3', 'Level 3 · Plate-Loaded Low Row', 'ADVANCED', '4 x 8-12 reps', 'Iso-lateral heavy horizontal rowing.'

    UNION ALL SELECT 'GYM_WEIGHTED_PULL_UP', 1, 'WPU_L1', 'Level 1 · Machine-Assisted Pull-Up', 'BEGINNER', '3-4 x 8-12 reps', 'Build full-ROM vertical pulling with counterbalance support.'
    UNION ALL SELECT 'GYM_WEIGHTED_PULL_UP', 2, 'WPU_L2', 'Level 2 · Weighted Pull-Up & Chin-Up', 'ADVANCED', '4 x 5-8 reps', 'Dip-belt weighted pronated and supinated vertical pulls.'

    -- Gym Legs
    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 1, 'BS_L1', 'Level 1 · Goblet & Guided Squat', 'BEGINNER', '3-4 x 10-14 reps', 'Goblet, landmine, and Smith machine squats.'
    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 2, 'BS_L2', 'Level 2 · Barbell Back & Front Squat', 'INTERMEDIATE', '4 x 5-8 reps', 'Primary free-weight bilateral squat compounds.'
    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 3, 'BS_L3', 'Level 3 · Pause, Box & SSB Squat', 'ADVANCED', '4 x 4-8 reps', 'Pause, box, safety squat bar, and Zercher squat variations.'

    UNION ALL SELECT 'GYM_ROMANIAN_DEADLIFT', 1, 'RDL_L1', 'Level 1 · Dumbbell & Staggered RDL', 'BEGINNER', '3-4 x 8-12 reps', 'Learn hip-hinge mechanics with dumbbells or trap bar.'
    UNION ALL SELECT 'GYM_ROMANIAN_DEADLIFT', 2, 'RDL_L2', 'Level 2 · Barbell Romanian Deadlift', 'INTERMEDIATE', '4 x 6-10 reps', 'Primary posterior-chain barbell hinge progression.'
    UNION ALL SELECT 'GYM_ROMANIAN_DEADLIFT', 3, 'RDL_L3', 'Level 3 · Paused RDL & Good Morning', 'ADVANCED', '3-4 x 6-8 reps', 'Paused barbell RDL and barbell good morning.'

    UNION ALL SELECT 'GYM_LEG_PRESS', 1, 'LPR_L1', 'Level 1 · 45-Degree Leg Press', 'BEGINNER', '4 x 10-15 reps', 'Controlled sled leg press through deep flexion.'
    UNION ALL SELECT 'GYM_LEG_PRESS', 2, 'LPR_L2', 'Level 2 · Hack & Pendulum Squat', 'INTERMEDIATE', '4 x 8-12 reps', 'Quad-biased narrow leg press, hack squat, and pendulum squat.'
    UNION ALL SELECT 'GYM_LEG_PRESS', 3, 'LPR_L3', 'Level 3 · Unilateral Machine Press', 'ADVANCED', '3 x 8-12 reps/leg', 'Single-leg leg press and single-leg hack squat.'

    UNION ALL SELECT 'GYM_LEG_EXTENSION', 1, 'LE_L1', 'Level 1 · Seated Leg Extension', 'BEGINNER', '3-4 x 12-15 reps', 'Controlled bilateral knee extension with peak pause.'
    UNION ALL SELECT 'GYM_LEG_EXTENSION', 2, 'LE_L2', 'Level 2 · Single-Leg Extension', 'INTERMEDIATE', '3 x 10-12 reps/leg', 'Unilateral quadriceps isolation to correct imbalances.'

    UNION ALL SELECT 'GYM_LEG_CURL', 1, 'LC_L1', 'Level 1 · Seated & Lying Leg Curl', 'BEGINNER', '3-4 x 10-15 reps', 'Seated and prone machine hamstring curls.'
    UNION ALL SELECT 'GYM_LEG_CURL', 2, 'LC_L2', 'Level 2 · Unilateral & GHD Curl', 'ADVANCED', '3-4 x 8-12 reps', 'Standing single-leg curl, stability ball curl, and GHD raise.'

    UNION ALL SELECT 'GYM_CALF_RAISE', 1, 'CR_L1', 'Level 1 · Seated & Sled Calf Raise', 'BEGINNER', '4 x 15-20 reps', 'Bent-knee soleus and leg press calf work.'
    UNION ALL SELECT 'GYM_CALF_RAISE', 2, 'CR_L2', 'Level 2 · Standing & Donkey Calf Raise', 'INTERMEDIATE', '4 x 12-16 reps', 'Straight-knee gastrocnemius loading with 2s bottom stretch.'
    UNION ALL SELECT 'GYM_CALF_RAISE', 3, 'CR_L3', 'Level 3 · Unilateral Calf Raise', 'INTERMEDIATE', '3 x 10-15 reps/leg', 'Single-leg machine and kettlebell calf raises.'

    -- Gym Core
    UNION ALL SELECT 'GYM_CABLE_CRUNCH', 1, 'CC_L1', 'Level 1 · Kneeling Cable Crunch', 'BEGINNER', '3-4 x 12-16 reps', 'Loaded spinal flexion with fixed hips.'
    UNION ALL SELECT 'GYM_CABLE_CRUNCH', 2, 'CC_L2', 'Level 2 · Weighted Decline & Anti-Rotation', 'INTERMEDIATE', '3 x 10-15 reps', 'Weighted decline crunch, Pallof press, and cable woodchoppers.'

    UNION ALL SELECT 'GYM_HANGING_LEG_RAISE', 1, 'HLR_L1', 'Level 1 · Weighted Captain Chair Raise', 'INTERMEDIATE', '3 x 10-15 reps', 'Controlled pelvis curl with light dumbbell support.'
    UNION ALL SELECT 'GYM_HANGING_LEG_RAISE', 2, 'HLR_L2', 'Level 2 · Strict Hanging Bar Raise', 'INTERMEDIATE', '3-4 x 8-12 reps', 'Strict dead-hang leg raise without swing.'

    UNION ALL SELECT 'GYM_AB_WHEEL', 1, 'AW_L1', 'Level 1 · Kneeling Ab Wheel Rollout', 'INTERMEDIATE', '3-4 x 10-15 reps', 'Anti-extension kneeling rollout with posterior pelvic tilt.'
    UNION ALL SELECT 'GYM_AB_WHEEL', 2, 'AW_L2', 'Level 2 · Standing Rollout & Weighted Plank', 'ELITE', '4 x 4-8 reps', 'Standing ab wheel rollout and weighted plank plate transfers.'

    -- Gym Shoulders & Arms
    UNION ALL SELECT 'GYM_LATERAL_RAISE', 1, 'LR_L1', 'Level 1 · Dumbbell Lateral Raise', 'BEGINNER', '4 x 12-20 reps', 'Standing and seated dumbbell lateral raises.'
    UNION ALL SELECT 'GYM_LATERAL_RAISE', 2, 'LR_L2', 'Level 2 · Cable & Full-Arc Lateral', 'INTERMEDIATE', '3-4 x 12-16 reps', 'Behind-the-back cable lateral raise and Lu raises.'
    UNION ALL SELECT 'GYM_LATERAL_RAISE', 3, 'LR_L3', 'Level 3 · Rear Deltoid Isolation', 'INTERMEDIATE', '3-4 x 12-18 reps', 'Reverse pec deck and bent-over rear delt flyes.'

    UNION ALL SELECT 'GYM_BICEP_CURL', 1, 'BC_L1', 'Level 1 · Dumbbell & Machine Curl', 'BEGINNER', '3-4 x 10-14 reps', 'Alternating curls, hammer curls, and machine bicep curls.'
    UNION ALL SELECT 'GYM_BICEP_CURL', 2, 'BC_L2', 'Level 2 · Barbell & Preacher Curl', 'INTERMEDIATE', '3-4 x 8-12 reps', 'Standing barbell curls, EZ-bar preacher curls, and rope hammer curls.'
    UNION ALL SELECT 'GYM_BICEP_CURL', 3, 'BC_L3', 'Level 3 · Incline & Bayesian Stretch Curl', 'INTERMEDIATE', '3 x 10-15 reps', 'Incline dumbbell curls, Bayesian cable curls, and spider curls.'

    UNION ALL SELECT 'GYM_TRICEPS_EXTENSION', 1, 'TE_L1', 'Level 1 · Cable Pushdown & Machine Dip', 'BEGINNER', '3-4 x 12-15 reps', 'Rope and bar pushdowns plus seated machine dips.'
    UNION ALL SELECT 'GYM_TRICEPS_EXTENSION', 2, 'TE_L2', 'Level 2 · Overhead Triceps Extension', 'INTERMEDIATE', '3-4 x 10-15 reps', 'Cable and dumbbell overhead long-head extensions.'
    UNION ALL SELECT 'GYM_TRICEPS_EXTENSION', 3, 'TE_L3', 'Level 3 · Skull Crusher Variations', 'INTERMEDIATE', '3-4 x 8-12 reps', 'EZ-bar and incline dumbbell skull crushers.'

    UNION ALL SELECT 'GYM_FACE_PULL', 1, 'FP_L1', 'Level 1 · Rope Face Pull & External Rotation', 'BEGINNER', '3-4 x 15-20 reps', 'Cable face pull and standing external shoulder rotation.'
    UNION ALL SELECT 'GYM_FACE_PULL', 2, 'FP_L2', 'Level 2 · Y-Raise & Cuban Press', 'INTERMEDIATE', '3 x 10-15 reps', 'Cable Y-raises, dumbbell Cuban presses, and Powell raises.'

    -- Gym Mobility
    UNION ALL SELECT 'GYM_SHOULDER_MOBILITY', 1, 'GSM_L1', 'Level 1 · Shoulder & Thoracic Prep', 'BEGINNER', '3 x 8-12 reps', 'Standing kettlebell halos and dumbbell thoracic pullovers.'
    UNION ALL SELECT 'GYM_HIP_ANKLE_MOBILITY', 1, 'GHM_L1', 'Level 1 · Squat & Hinge Mobility', 'BEGINNER', '3 x 8-15 reps', 'Banded ankle distraction, goblet cossack, and loaded stretches.'
) v
JOIN skills s ON s.code = v.skill_code
WHERE NOT EXISTS (
    SELECT 1 FROM skill_progression_levels spl WHERE spl.skill_id = s.id AND (spl.level_number = v.level_number OR spl.code = v.code)
);

-- 5. Mark explicitly shared hybrid exercises as BOTH when already present so they support both disciplines without duplication
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS secondary_muscles VARCHAR(255);
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS exercise_type VARCHAR(50);
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS recommended_sets INT;
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS recommended_reps VARCHAR(64);
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS recommended_rest_seconds INT;
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS common_mistakes TEXT;
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS safety_considerations TEXT;
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS progression_notes TEXT;
ALTER TABLE exercises ADD COLUMN IF NOT EXISTS aliases TEXT;

UPDATE exercises
SET training_type = 'BOTH'
WHERE UPPER(code) IN ('HANGING_LEG_RAISE', 'HANGING_LEG_RAISE_STRICT_BAR', 'AB_WHEEL_ROLLOUT_KNEELING', 'STANDING_AB_WHEEL_ROLLOUT', 'WEIGHTED_PULL_UP', 'WEIGHTED_CHIN_UP')
   OR LOWER(TRIM(name)) IN ('hanging leg raise', 'ab wheel rollout (kneeling)', 'standing ab wheel rollout', 'weighted pull-up', 'weighted chin-up');

-- 6. Seed essential baseline exercises ONLY if neither code nor name exists (never duplicates existing catalog rows)
INSERT INTO exercises (
    name, code, category, difficulty, description, target_muscles, secondary_muscles,
    instructions, equipment, active, status, origin, training_type, exercise_type,
    recommended_sets, recommended_reps, recommended_rest_seconds, created_at
)
SELECT
    v.name, v.code, v.category, v.difficulty, v.description, v.target_muscles, v.secondary_muscles,
    v.instructions, v.equipment, TRUE, 'PUBLISHED', 'MANUAL', v.training_type, v.exercise_type,
    v.recommended_sets, v.recommended_reps, v.recommended_rest_seconds, CURRENT_TIMESTAMP
FROM (
    -- Essential Calisthenics Skill Progressions not in V2/V30/V56
    SELECT CAST('Planche Lean' AS VARCHAR(100)) AS name, CAST('PLANCHE_LEAN' AS VARCHAR(50)) AS code, CAST('ISOMETRIC' AS VARCHAR(50)) AS category, CAST('BEGINNER' AS VARCHAR(50)) AS difficulty,
           CAST('Foundational straight-arm forward lean conditioning wrists, biceps tendons, and anterior deltoids.' AS TEXT) AS description,
           CAST('Anterior Deltoids, Forearms' AS TEXT) AS target_muscles, CAST('Chest, Triceps, Rectus Abdominis' AS TEXT) AS secondary_muscles,
           CAST('Lock elbows completely, protract scapula, and lean shoulders forward past wrists.' AS TEXT) AS instructions,
           CAST('Floor or Parallettes' AS VARCHAR(255)) AS equipment, CAST('CALISTHENICS' AS VARCHAR(32)) AS training_type, CAST('ISOMETRIC' AS VARCHAR(32)) AS exercise_type,
           4 AS recommended_sets, CAST('20-30s' AS VARCHAR(64)) AS recommended_reps, 75 AS recommended_rest_seconds
    UNION ALL SELECT 'Frog Stand', 'FROG_STAND', 'SKILL', 'BEGINNER',
           'Entry-level hand balance resting knees against upper triceps to learn forward weight transfer.',
           'Anterior Deltoids, Forearms', 'Core, Triceps',
           'Place hands shoulder-width apart, rest knees on triceps, and lean forward until feet lift.',
           'Floor or Parallettes', 'CALISTHENICS', 'SKILL', 4, '20-30s', 60
    UNION ALL SELECT 'Pseudo Planche Push-Up', 'PSEUDO_PLANCHE_PUSH_UP', 'PUSH', 'INTERMEDIATE',
           'Dynamic pushing movement with hands near hips and pronounced forward shoulder lean.',
           'Anterior Deltoids, Chest', 'Triceps, Core',
           'Turn fingers outward near hip line, lean shoulders past wrists, lower chest and press to protraction.',
           'Floor or Parallettes', 'CALISTHENICS', 'COMPOUND', 4, '6-10', 90
    UNION ALL SELECT 'Wall Handstand Push-Up', 'WALL_HANDSTAND_PUSH_UP', 'PUSH', 'ADVANCED',
           'Full vertical pressing movement supported against a wall for overhead strength.',
           'Front Deltoids, Triceps', 'Lateral Deltoids, Upper Trapezius, Core',
           'Kick into hollow wall handstand, lower head forward into tripod, and press vertically to lockout.',
           'Wall', 'CALISTHENICS', 'COMPOUND', 4, '4-8', 120
    UNION ALL SELECT 'Diamond Push-Up', 'DIAMOND_PUSH_UP', 'PUSH', 'INTERMEDIATE',
           'Close-grip push-up variation emphasizing triceps lockout and inner pectoral activation.',
           'Triceps, Chest', 'Anterior Deltoids, Core',
           'Form a diamond under sternum, keep elbows close to ribs, and press to full extension.',
           'Floor', 'CALISTHENICS', 'COMPOUND', 3, '8-12', 75
    UNION ALL SELECT 'Archer Push-Up', 'ARCHER_PUSH_UP', 'PUSH', 'ADVANCED',
           'Wide-stance unilateral push-up shifting load onto one arm while keeping the other straight.',
           'Chest, Anterior Deltoids, Triceps', 'Core',
           'Adopt a wide stance, lower chest toward one hand while extending opposite arm straight.',
           'Floor', 'CALISTHENICS', 'COMPOUND', 3, '6-8/side', 90
    UNION ALL SELECT 'Gymnastic Ring Dip', 'GYMNASTIC_RING_DIP', 'PUSH', 'ADVANCED',
           'Unstable vertical pressing on gymnastic rings building deep shoulder and triceps control.',
           'Triceps, Chest', 'Front Deltoids, Core',
           'Keep rings tight to sides, lower past parallel, and press to rings-turned-out lockout.',
           'Gymnastic Rings', 'CALISTHENICS', 'COMPOUND', 4, '6-10', 90
    UNION ALL SELECT 'Tuck Front Lever', 'TUCK_FRONT_LEVER', 'SKILL', 'BEGINNER',
           'Foundational Front Lever hold with knees tucked tightly to chest and torso horizontal.',
           'Latissimus Dorsi, Rear Deltoids', 'Core, Forearms',
           'Depress scapula, keep elbows straight, pull down with lats, and hold torso horizontal.',
           'Pull-Up Bar or Rings', 'CALISTHENICS', 'ISOMETRIC', 4, '15-25s', 90
    UNION ALL SELECT 'Advanced Tuck Front Lever', 'ADV_TUCK_FRONT_LEVER', 'SKILL', 'INTERMEDIATE',
           'Front Lever hold with flat lumbar spine and thighs perpendicular to torso.',
           'Latissimus Dorsi, Rear Deltoids', 'Rhomboids, Core',
           'Keep elbows locked and extend knees away from chest until thighs are vertical.',
           'Pull-Up Bar or Rings', 'CALISTHENICS', 'ISOMETRIC', 4, '10-20s', 90
    UNION ALL SELECT 'Straddle Front Lever', 'STRADDLE_FRONT_LEVER', 'SKILL', 'ADVANCED',
           'Penultimate Front Lever hold with straight legs abducted wide.',
           'Latissimus Dorsi, Core', 'Rear Deltoids, Glutes, Forearms',
           'Lock arms, depress scapula, and hold straight torso and straddled legs horizontal.',
           'Pull-Up Bar or Rings', 'CALISTHENICS', 'ISOMETRIC', 4, '6-12s', 120
    UNION ALL SELECT 'German Hang', 'GERMAN_HANG', 'ISOMETRIC', 'BEGINNER',
           'Shoulder extension hang preparing biceps tendons and anterior capsule for Back Lever.',
           'Front Deltoids, Chest', 'Biceps, Forearms',
           'Rotate carefully through inverted hang into bottom extension hang with straight arms.',
           'Gymnastic Rings or Low Bar', 'CALISTHENICS', 'ISOMETRIC', 3, '20-30s', 60
    UNION ALL SELECT 'Skin the Cat', 'SKIN_THE_CAT', 'SKILL', 'BEGINNER',
           'Full-range straight-arm rotation from dead hang into German Hang and back.',
           'Latissimus Dorsi, Front Deltoids', 'Core, Biceps',
           'Tuck knees smoothly through arms into German Hang and pull back to dead hang.',
           'Gymnastic Rings or Pull-Up Bar', 'CALISTHENICS', 'COMPOUND', 3, '3-5', 75
    UNION ALL SELECT 'Tuck Back Lever', 'TUCK_BACK_LEVER', 'SKILL', 'INTERMEDIATE',
           'Horizontal face-down suspension hold with knees tucked to chest.',
           'Front Deltoids, Chest', 'Biceps, Erector Spinae',
           'Lower from inverted tuck hang to horizontal face-down hold with straight elbows.',
           'Gymnastic Rings or Pull-Up Bar', 'CALISTHENICS', 'ISOMETRIC', 4, '15-25s', 90
    UNION ALL SELECT 'Full Back Lever', 'FULL_BACK_LEVER', 'SKILL', 'ADVANCED',
           'Horizontal face-down hold with full straight-body extension.',
           'Front Deltoids, Chest, Latissimus Dorsi', 'Biceps, Core, Glutes',
           'Lock elbows, slightly protract shoulders, and hold straight body parallel to ground.',
           'Gymnastic Rings or Pull-Up Bar', 'CALISTHENICS', 'ISOMETRIC', 4, '8-15s', 120
    UNION ALL SELECT 'Archer Pull-Up', 'ARCHER_PULL_UP', 'PULL', 'ADVANCED',
           'Wide-grip unilateral pull-up shifting chest toward one hand while extending opposite arm.',
           'Latissimus Dorsi, Biceps', 'Rhomboids, Forearms',
           'Grip wide on bar, pull chest toward working hand while keeping opposite arm straight.',
           'Pull-Up Bar', 'CALISTHENICS', 'COMPOUND', 4, '4-6/side', 90
    UNION ALL SELECT 'Strict One-Arm Chin-Up', 'ONE_ARM_PULL_UP', 'PULL', 'ELITE',
           'Unassisted single-arm vertical pull from dead hang to chin over bar.',
           'Latissimus Dorsi, Biceps', 'Forearms, Obliques',
           'Engage active shoulder depression and drive elbow toward ribcage on one arm.',
           'Pull-Up Bar or Ring', 'CALISTHENICS', 'COMPOUND', 4, '1-3/arm', 180
    UNION ALL SELECT 'Pistol Squat', 'PISTOL_SQUAT', 'LEGS', 'ADVANCED',
           'Full-depth single-leg squat with non-working leg extended horizontally forward.',
           'Quadriceps, Glutes', 'Hamstrings, Core',
           'Extend free leg ahead, descend smoothly to full depth on stance leg, and press up.',
           'Floor', 'CALISTHENICS', 'COMPOUND', 4, '4-8/leg', 120
    UNION ALL SELECT 'Shrimp Squat', 'SHRIMP_SQUAT', 'LEGS', 'ADVANCED',
           'Single-leg squat holding trailing foot behind glutes until rear knee taps floor.',
           'Quadriceps, Glutes', 'Hamstrings, Core',
           'Hold rear ankle behind glutes, touch knee softly to floor, and drive up.',
           'Floor', 'CALISTHENICS', 'COMPOUND', 4, '5-8/leg', 90
    UNION ALL SELECT 'Nordic Hamstring Curl', 'NORDIC_HAMSTRING_CURL', 'LEGS', 'ADVANCED',
           'High-tension bodyweight knee flexion lowering torso forward under hamstring control.',
           'Hamstrings, Glutes', 'Calves, Core',
           'Anchor ankles, brace hips open, lower chest slowly forward, and pull back using hamstrings.',
           'Partner, Strap, or Anchored Bar', 'CALISTHENICS', 'ISOLATION', 4, '4-6', 120
    UNION ALL SELECT 'V-Sit Hold', 'V_SIT', 'CORE', 'ELITE',
           'Advanced pike compression support hold lifting straight legs past 45 degrees.',
           'Rectus Abdominis, Transverse Abdominis', 'Triceps, Rear Deltoids, Quadriceps',
           'Press shoulders forward behind hips while lifting straight legs high into a V.',
           'Floor or Parallettes', 'CALISTHENICS', 'ISOMETRIC', 4, '8-15s', 120
    UNION ALL SELECT 'Dragon Flag', 'DRAGON_FLAG', 'CORE', 'ADVANCED',
           'Full-body straight-lever core exercise lowering rigid torso from upper back support.',
           'Rectus Abdominis, Obliques', 'Latissimus Dorsi, Glutes',
           'Grip bench behind head, keep rigid straight line, lower toward horizontal, and lift back up.',
           'Bench', 'CALISTHENICS', 'COMPOUND', 4, '4-6', 120
    UNION ALL SELECT 'Tuck Human Flag', 'TUCK_HUMAN_FLAG', 'SKILL', 'ADVANCED',
           'Horizontal lateral pole hold with knees tucked into chest.',
           'Obliques, Latissimus Dorsi', 'Shoulders, Forearms',
           'Push through bottom arm, pull with top arm, and hold tucked torso horizontal.',
           'Vertical Pole or Stall Bars', 'CALISTHENICS', 'ISOMETRIC', 4, '8-15s/side', 120
    UNION ALL SELECT 'Full Human Flag', 'HUMAN_FLAG', 'SKILL', 'ELITE',
           'Signature lateral horizontal hold suspended perpendicular to a vertical pole.',
           'Obliques, Latissimus Dorsi', 'Shoulders, Triceps, Glutes',
           'Lock both arms, depress shoulders, and hold full body parallel to ground.',
           'Vertical Pole or Stall Bars', 'CALISTHENICS', 'ISOMETRIC', 4, '5-12s/side', 150
    UNION ALL SELECT 'Shoulder Dislocates (Stick/Band)', 'SHOULDER_MOBILITY', 'MOBILITY', 'BEGINNER',
           'Full-circle overhead shoulder pass-through improving overhead and extension mobility.',
           'Front Deltoids, Chest', 'Upper Trapezius, Latissimus Dorsi',
           'Keep elbows locked while sweeping stick or band smoothly overhead behind hips.',
           'PVC Pipe or Resistance Band', 'CALISTHENICS', 'MOBILITY', 3, '10-15', 30
    UNION ALL SELECT 'Wrist Conditioning Push-Up Sequence', 'WRIST_MOBILITY', 'MOBILITY', 'BEGINNER',
           'Structured wrist flexion, extension, and palm pulse sequence for handstand and planche prep.',
           'Forearms', 'Shoulders',
           'Perform controlled palm pulses, first-knuckle lifts, and dorsal wrist leans with straight arms.',
           'Floor or Mat', 'CALISTHENICS', 'MOBILITY', 2, '10-15/phase', 30
    UNION ALL SELECT '90/90 Hip Internal-External Switches', 'HIP_MOBILITY', 'MOBILITY', 'BEGINNER',
           'Seated hip capsule rotation drill alternating internal and external hip rotation.',
           'Glutes, Adductors', 'Core',
           'Sit tall with knees at 90/90 and rotate both knees smoothly side to side.',
           'Floor or Mat', 'CALISTHENICS', 'MOBILITY', 3, '8-12/side', 45
    UNION ALL SELECT 'Middle Split Frog Slide', 'MIDDLE_SPLIT', 'FLEXIBILITY', 'INTERMEDIATE',
           'Adductor and hip capsule opener progressing toward full middle split.',
           'Adductors', 'Glutes',
           'Align ankles behind knees with flexed feet and sink hips gently toward floor.',
           'Mat or Sliders', 'CALISTHENICS', 'FLEXIBILITY', 3, '45-60s', 45
    UNION ALL SELECT 'Front Split Slider Hold', 'FRONT_SPLIT', 'FLEXIBILITY', 'ADVANCED',
           'Active front split hold lengthening hamstrings and hip flexors with square pelvis.',
           'Hamstrings, Quadriceps', 'Adductors, Glutes',
           'Square hips forward on blocks and lower smoothly into front split stretch.',
           'Yoga Blocks and Mat', 'CALISTHENICS', 'FLEXIBILITY', 3, '45-60s/side', 45

    -- Essential Gym / Weight Training Exercises (strictly GYM or BOTH)
    UNION ALL SELECT 'Barbell Bench Press', 'BENCH_PRESS', 'CHEST', 'INTERMEDIATE',
           'Foundational horizontal barbell press for chest, front deltoids, and triceps strength.',
           'Chest, Front Deltoids', 'Triceps',
           'Retract shoulder blades into bench, lower bar to lower sternum, press cleanly to lockout.',
           'Barbell, Flat Bench', 'GYM', 'COMPOUND', 4, '5-8', 150
    UNION ALL SELECT 'Incline Barbell Bench Press', 'INCLINE_BENCH_PRESS', 'CHEST', 'INTERMEDIATE',
           '30-degree incline barbell press emphasizing clavicular pectorals and anterior deltoids.',
           'Chest, Front Deltoids', 'Triceps',
           'Lower bar with control to upper chest with 45-degree tucked elbows and drive vertically.',
           'Barbell, Incline Bench', 'GYM', 'COMPOUND', 4, '6-10', 120
    UNION ALL SELECT 'Barbell Overhead Press', 'OVERHEAD_PRESS', 'SHOULDERS', 'INTERMEDIATE',
           'Standing strict barbell overhead press building shoulder and triceps strength.',
           'Front Deltoids, Lateral Deltoids', 'Triceps, Upper Trapezius, Core',
           'Brace glutes and abs, press barbell vertically past chin to locked stack overhead.',
           'Barbell, Squat Rack', 'GYM', 'COMPOUND', 4, '5-8', 120
    UNION ALL SELECT 'Seated Machine Chest Press', 'MACHINE_CHEST_PRESS', 'CHEST', 'BEGINNER',
           'Guided machine chest press providing stable horizontal pressing hypertrophy.',
           'Chest', 'Triceps, Front Deltoids',
           'Align handles with mid-chest, depress shoulders, and press through full arc.',
           'Chest Press Machine', 'GYM', 'COMPOUND', 3, '10-14', 75
    UNION ALL SELECT 'Barbell Bent-Over Row', 'BARBELL_ROW', 'BACK', 'INTERMEDIATE',
           'Free-weight horizontal barbell row targeting lats, rhomboids, and mid-back.',
           'Latissimus Dorsi, Rhomboids', 'Middle Trapezius, Biceps, Rear Deltoids',
           'Hinge torso 45 degrees with neutral spine and pull bar smoothly toward lower ribs.',
           'Barbell, Plates', 'GYM', 'COMPOUND', 4, '8-10', 120
    UNION ALL SELECT 'Lat Pulldown (Wide Overhand)', 'LAT_PULLDOWN', 'BACK', 'BEGINNER',
           'Vertical cable pulling exercise targeting latissimus dorsi width and upper back.',
           'Latissimus Dorsi, Teres Major', 'Biceps, Rhomboids',
           'Lean back slightly and drive elbows down toward hips until bar reaches upper chest.',
           'Lat Pulldown Cable Machine', 'GYM', 'COMPOUND', 4, '8-12', 90
    UNION ALL SELECT 'Seated Cable Row (V-Bar)', 'SEATED_CABLE_ROW', 'BACK', 'BEGINNER',
           'Horizontal cable row building mid-back thickness and scapular retraction.',
           'Rhomboids, Middle Trapezius, Latissimus Dorsi', 'Biceps, Rear Deltoids',
           'Sit tall without torso swing and pull V-bar cleanly to lower sternum.',
           'Seated Cable Row Machine', 'GYM', 'COMPOUND', 4, '10-14', 75
    UNION ALL SELECT 'Weighted Pull-Up', 'WEIGHTED_PULL_UP', 'PULL', 'ADVANCED',
           'Weighted vertical pull-up using dip belt or vest for progressive upper-back strength.',
           'Latissimus Dorsi, Biceps', 'Rhomboids, Forearms, Core',
           'Start from dead hang with packed scapulae and pull chin clearly over bar.',
           'Pull-Up Bar and Dip Belt', 'BOTH', 'COMPOUND', 4, '5-8', 120
    UNION ALL SELECT 'Barbell Back Squat', 'BARBELL_SQUAT', 'LEGS', 'INTERMEDIATE',
           'Foundational barbell lower-body compound lift targeting quads, glutes, and core.',
           'Quadriceps, Glutes', 'Hamstrings, Erector Spinae, Core',
           'Set bar on upper traps, brace core, descend below parallel, and drive tall.',
           'Barbell, Squat Rack, Plates', 'GYM', 'COMPOUND', 4, '5-8', 150
    UNION ALL SELECT 'Barbell Romanian Deadlift', 'ROMANIAN_DEADLIFT', 'LEGS', 'INTERMEDIATE',
           'Hip-hinge barbell progression targeting hamstrings and glutes.',
           'Hamstrings, Glutes', 'Erector Spinae, Forearms',
           'Keep soft knee bend, push hips back with bar close to thighs, and drive hips forward.',
           'Barbell, Plates', 'GYM', 'COMPOUND', 4, '6-10', 120
    UNION ALL SELECT 'Leg Press (45-Degree)', 'LEG_PRESS', 'LEGS', 'BEGINNER',
           'Machine compound lower-body press allowing high-output quadriceps and glute loading.',
           'Quadriceps, Glutes', 'Hamstrings, Adductors',
           'Keep lower back planted against pad, lower sled smoothly, and press without locking violently.',
           '45-Degree Leg Press Machine', 'GYM', 'COMPOUND', 4, '10-15', 90
    UNION ALL SELECT 'Leg Extension Machine', 'LEG_EXTENSION', 'LEGS', 'BEGINNER',
           'Seated machine isolation exercise targeting the quadriceps.',
           'Quadriceps', 'Core',
           'Align knee joint with machine pivot and extend smoothly to peak quad contraction.',
           'Seated Leg Extension Machine', 'GYM', 'ISOLATION', 3, '12-15', 60
    UNION ALL SELECT 'Seated Leg Curl Machine', 'LEG_CURL', 'LEGS', 'BEGINNER',
           'Machine knee-flexion isolation targeting the hamstrings.',
           'Hamstrings', 'Calves',
           'Lock thigh pad securely above knees and curl heels smoothly under seat.',
           'Seated Leg Curl Machine', 'GYM', 'ISOLATION', 3, '10-15', 60
    UNION ALL SELECT 'Standing Machine Calf Raise', 'CALF_RAISE', 'LEGS', 'BEGINNER',
           'Standing calf raise machine targeting gastrocnemius and soleus.',
           'Calves', 'Core',
           'Pause 2 seconds in full bottom stretch and drive onto big-toe ball.',
           'Standing Calf Raise Machine', 'GYM', 'ISOLATION', 4, '12-16', 60
    UNION ALL SELECT 'Cable Crunch (Kneeling Rope)', 'CABLE_CRUNCH', 'CORE', 'BEGINNER',
           'Loaded kneeling cable trunk flexion isolating the rectus abdominis.',
           'Rectus Abdominis', 'Obliques',
           'Keep hips stacked over knees and curl ribs down toward pelvis.',
           'Cable Stack and Rope', 'GYM', 'ISOLATION', 3, '12-16', 60
    UNION ALL SELECT 'Hanging Leg Raise', 'HANGING_LEG_RAISE', 'CORE', 'INTERMEDIATE',
           'Strict hanging leg raise building lower abdominal and hip flexor strength.',
           'Rectus Abdominis', 'Obliques, Forearms, Latissimus Dorsi',
           'From still dead hang, lift straight legs cleanly to bar height without swinging.',
           'Pull-Up Bar', 'BOTH', 'COMPOUND', 3, '8-12', 75
    UNION ALL SELECT 'Ab Wheel Rollout (Kneeling)', 'AB_WHEEL', 'CORE', 'INTERMEDIATE',
           'Loaded anti-extension core rollout using an ab wheel.',
           'Rectus Abdominis, Transverse Abdominis', 'Latissimus Dorsi, Triceps',
           'Tuck pelvis posteriorly and roll out forward under control before pulling back.',
           'Ab Wheel and Mat', 'BOTH', 'COMPOUND', 3, '10-15', 75
    UNION ALL SELECT 'Dumbbell Lateral Raise', 'LATERAL_RAISE', 'SHOULDERS', 'BEGINNER',
           'Isolation exercise targeting the lateral deltoids.',
           'Lateral Deltoids', 'Upper Trapezius',
           'Raise dumbbells in 30-degree scapular plane to shoulder height with controlled eccentric.',
           'Dumbbells', 'GYM', 'ISOLATION', 4, '12-20', 45
    UNION ALL SELECT 'Standing Barbell Bicep Curl', 'BICEP_CURL', 'BICEPS', 'BEGINNER',
           'Classic free-weight elbow flexor builder targeting biceps brachii.',
           'Biceps', 'Forearms',
           'Pin elbows beside torso and curl barbell smoothly without lower-back swing.',
           'Barbell, Plates', 'GYM', 'ISOLATION', 3, '8-12', 75
    UNION ALL SELECT 'Cable Triceps Pushdown (Rope)', 'TRICEPS_EXTENSION', 'TRICEPS', 'BEGINNER',
           'Cable elbow extension isolating the lateral and medial heads of the triceps.',
           'Triceps', 'Forearms',
           'Keep elbows tucked to ribs and extend rope smoothly down until arms lock.',
           'Cable Stack and Rope', 'GYM', 'ISOLATION', 3, '12-15', 45
    UNION ALL SELECT 'Cable Face Pull', 'FACE_PULL', 'BACK', 'BEGINNER',
           'Cable external-rotation pull strengthening rear deltoids and rotator cuff.',
           'Rear Deltoids, Rhomboids', 'Middle Trapezius',
           'Pull rope toward face while externally rotating thumbs backward.',
           'Cable Stack and Rope', 'GYM', 'ISOLATION', 3, '15-20', 45
    UNION ALL SELECT 'Standing Kettlebell Halo', 'GYM_SHOULDER_MOBILITY', 'MOBILITY', 'BEGINNER',
           'Controlled circular kettlebell orbit improving shoulder capsule and thoracic mobility.',
           'Front Deltoids, Upper Trapezius', 'Core',
           'Keep ribs stacked over hips while orbiting light kettlebell smoothly around head.',
           'Light Kettlebell', 'GYM', 'MOBILITY', 3, '8/direction', 45
    UNION ALL SELECT 'Banded Ankle Distraction Mobilization', 'GYM_HIP_ANKLE_MOBILITY', 'MOBILITY', 'BEGINNER',
           'Joint mobilization improving ankle dorsiflexion for deep squat mechanics.',
           'Calves', 'Quadriceps',
           'Anchor heavy band low across ankle crease while driving knee forward over toes.',
           'Resistance Band', 'BOTH', 'MOBILITY', 2, '15/ankle', 30
) v
WHERE NOT EXISTS (
    SELECT 1 FROM exercises ex
    WHERE UPPER(ex.code) = UPPER(v.code)
       OR LOWER(TRIM(ex.name)) = LOWER(TRIM(v.name))
);

-- 7. Populate skill_exercise_links for all canonical catalog exercises (reusing existing IDs, strictly respecting trainingType)
INSERT INTO skill_exercise_links (skill_id, progression_level_id, exercise_id, relation_type, display_order)
SELECT
    s.id,
    spl.id,
    matched.exercise_id,
    m.relation_type,
    m.display_order
FROM (
    -- CAL_PLANCHE
    SELECT CAST('CAL_PLANCHE' AS VARCHAR(64)) AS skill_code, CAST('PLANCHE_L1' AS VARCHAR(80)) AS level_code, CAST('PLANCHE_LEAN' AS VARCHAR(64)) AS ex_code, CAST('Planche Lean' AS VARCHAR(120)) AS ex_name, CAST('PRIMARY' AS VARCHAR(32)) AS relation_type, 1 AS display_order
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L1', 'PLANCHE_LEAN_HOLD', 'Planche Lean Hold', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L2', 'FROG_STAND', 'Frog Stand', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L2', 'CROW_POSE_KAKASANA', 'Crow Pose (Kakasana)', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L3', 'TUCK_PLANCHE', 'Tuck Planche', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L3', 'TUCK_PLANCHE_HOLD', 'Tuck Planche Hold', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L4', 'ADV_TUCK_PLANCHE', 'Advanced Tuck Planche', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L5', 'STRADDLE_PLANCHE', 'Straddle Planche', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L5', 'FULL_PLANCHE', 'Full Planche', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L5', 'PLANCHE', 'Planche', 'PRIMARY', 3
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L3', 'PSEUDO_PLANCHE_PUSH_UP', 'Pseudo Planche Push-Up', 'SUPPORTING', 4
    UNION ALL SELECT 'CAL_PLANCHE', 'PLANCHE_L4', 'TUCK_PLANCHE_PUSH_UP', 'Tuck Planche Push-Up', 'SUPPORTING', 5

    -- CAL_HSPU
    UNION ALL SELECT 'CAL_HSPU', 'HSPU_L1', 'PIKE_PUSH_UP', 'Pike Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HSPU', 'HSPU_L2', 'ELEVATED_PIKE_PUSH_UP', 'Elevated Pike Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HSPU', 'HSPU_L3', 'WALL_HANDSTAND_PUSH_UP', 'Wall Handstand Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HSPU', 'HSPU_L4', 'FREESTANDING_HANDSTAND_PUSH_UP', 'Freestanding Handstand Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HSPU', 'HSPU_L2', 'HANDSTAND', 'Handstand', 'PREREQUISITE', 2

    -- CAL_PUSH_UP_PROGRESSIONS
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L1', 'INCLINE_PUSH_UP', 'Incline Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L1', 'SCAPULAR_PUSH_UP', 'Scapular Push-Up', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L1', 'PUSH_UP', 'Push-Up', 'PRIMARY', 3
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L2', 'DIAMOND_PUSH_UP', 'Diamond Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L2', 'DECLINE_PUSH_UP', 'Decline Push-Up', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L2', 'GYMNASTIC_RING_PUSH_UP', 'Gymnastic Ring Push-Up', 'PRIMARY', 3
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L3', 'ARCHER_PUSH_UP', 'Archer Push-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L3', 'PSEUDO_PLANCHE_PUSH_UP', 'Pseudo Planche Push-Up', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_PUSH_UP_PROGRESSIONS', 'PUSHUP_L4', 'STRICT_ONE_ARM_PUSH_UP', 'Strict One-Arm Push-Up', 'PRIMARY', 1

    -- CAL_DIP_PROGRESSIONS
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L1', 'DIPS', 'Parallel Bar Dips', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L1', 'STRAIGHT_ARM_SUPPORT_HOLD_RINGS', 'Straight-Arm Support Hold (Rings)', 'PREREQUISITE', 2
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L2', 'STRAIGHT_BAR_DIP', 'Straight Bar Dip', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L2', 'PARALLEL_BAR_DIP', 'Parallel Bar Dip', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L3', 'GYMNASTIC_RING_DIP', 'Gymnastic Ring Dip', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L3', 'KOREAN_DIP', 'Korean Dip', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_DIP_PROGRESSIONS', 'DIP_L3', 'WEIGHTED_VEST_DIP', 'Weighted Vest Dip', 'PRIMARY', 3

    -- CAL_FRONT_LEVER
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L1', 'SCAPULAR_PULL_UP', 'Scapular Pull-Up', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L1', 'TUCK_FRONT_LEVER', 'Tuck Front Lever', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L2', 'ADV_TUCK_FRONT_LEVER', 'Advanced Tuck Front Lever', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L2', 'FRONT_LEVER_RAISES', 'Front Lever Raises', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L2', 'TUCK_FRONT_LEVER_ROW', 'Tuck Front Lever Row', 'SUPPORTING', 3
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L3', 'ONE_LEG_FRONT_LEVER', 'One-Leg Front Lever', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L3', 'FRONT_LEVER_NEGATIVE_DESCENT', 'Front Lever Negative Descent', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L4', 'STRADDLE_FRONT_LEVER', 'Straddle Front Lever', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_FRONT_LEVER', 'FL_L4', 'FRONT_LEVER', 'Front Lever', 'PRIMARY', 2

    -- CAL_BACK_LEVER
    UNION ALL SELECT 'CAL_BACK_LEVER', 'BL_L1', 'GERMAN_HANG', 'German Hang', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_BACK_LEVER', 'BL_L1', 'SKIN_THE_CAT', 'Skin the Cat', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_BACK_LEVER', 'BL_L2', 'TUCK_BACK_LEVER', 'Tuck Back Lever', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_BACK_LEVER', 'BL_L3', 'STRADDLE_BACK_LEVER', 'Straddle Back Lever', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_BACK_LEVER', 'BL_L4', 'FULL_BACK_LEVER', 'Full Back Lever', 'PRIMARY', 1

    -- CAL_ONE_ARM_PULL_UP
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L1', 'PULL_UP', 'Pull-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L2', 'WEIGHTED_PULL_UP', 'Weighted Pull-Up', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L2', 'COMMANDO_PULL_UP', 'Commando Pull-Up', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L3', 'ARCHER_PULL_UP', 'Archer Pull-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L3', 'TYPEWRITER_PULL_UP', 'Typewriter Pull-Up', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L3', 'ONE_ARM_HANG_PULL_REGRESSION', 'One-Arm Hang Pull Regression', 'SUPPORTING', 3
    UNION ALL SELECT 'CAL_ONE_ARM_PULL_UP', 'OAP_L4', 'ONE_ARM_PULL_UP', 'Strict One-Arm Chin-Up', 'PRIMARY', 1

    -- CAL_MUSCLE_UP
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L1', 'PULL_UP', 'Pull-Up', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L1', 'DIPS', 'Parallel Bar Dips', 'PREREQUISITE', 2
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L2', 'HIGH_PULL_UP', 'High Pull-Up', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L2', 'CHEST_TO_BAR_PULL_UP', 'Chest-to-Bar Pull-Up', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L2', 'FALSE_GRIP_PULL_UP', 'False-Grip Pull-Up', 'SUPPORTING', 3
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L2', 'STRAIGHT_BAR_DIP', 'Straight Bar Dip', 'SUPPORTING', 4
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L3', 'MUSCLE_UP', 'Muscle-Up', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_MUSCLE_UP', 'MU_L3', 'RING_MUSCLE_UP', 'Ring Muscle-Up', 'PRIMARY', 2

    -- CAL_PISTOL_SQUAT
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'PISTOL_L1', 'SQUAT', 'Bodyweight Squat', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'PISTOL_L1', 'BULGARIAN_SPLIT_SQUAT', 'Bulgarian Split Squat', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'PISTOL_L2', 'ASSISTED_PISTOL_SQUAT', 'Assisted Pistol Squat', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'PISTOL_L2', 'BOX_PISTOL_SQUAT', 'Box Pistol Squat', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'PISTOL_L3', 'PISTOL_SQUAT', 'Pistol Squat', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_PISTOL_SQUAT', 'PISTOL_L4', 'ELEVATED_DEFICIT_PISTOL_SQUAT', 'Elevated Deficit Pistol Squat', 'PRIMARY', 1

    -- CAL_SHRIMP_SQUAT
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 'SHRIMP_L1', 'SPLIT_SQUAT', 'Split Squat', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 'SHRIMP_L2', 'ASSISTED_SHRIMP_SQUAT_TOE_TOUCH', 'Assisted Shrimp Squat (Toe Touch)', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 'SHRIMP_L3', 'SHRIMP_SQUAT', 'Shrimp Squat', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_SHRIMP_SQUAT', 'SHRIMP_L4', 'DOUBLE_HOLD_SHRIMP_SQUAT', 'Double-Hold Shrimp Squat', 'PRIMARY', 1

    -- CAL_NORDIC_CURL
    UNION ALL SELECT 'CAL_NORDIC_CURL', 'NORDIC_L1', 'SINGLE_LEG_GLUTE_BRIDGE', 'Single-Leg Glute Bridge', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_NORDIC_CURL', 'NORDIC_L2', 'SLIDING_FLOOR_LEG_CURL', 'Sliding Floor Leg Curl', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_NORDIC_CURL', 'NORDIC_L2', 'RAZOR_HAMSTRING_CURL', 'Razor Hamstring Curl', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_NORDIC_CURL', 'NORDIC_L3', 'NORDIC_HAMSTRING_CURL', 'Nordic Hamstring Curl', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_NORDIC_CURL', 'NORDIC_L4', 'FULL_NORDIC_HAMSTRING_CURL', 'Full Nordic Hamstring Curl', 'PRIMARY', 1

    -- CAL_L_SIT
    UNION ALL SELECT 'CAL_L_SIT', 'LSIT_L1', 'HOLLOW_BODY_HOLD', 'Hollow Body Hold', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_L_SIT', 'LSIT_L2', 'SEATED_PIKE_LEG_LIFT', 'Seated Pike Leg Lift', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_L_SIT', 'LSIT_L2', 'L_SIT', 'L-Sit', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_L_SIT', 'LSIT_L3', 'L_SIT_PULL_UP', 'L-Sit Pull-Up', 'SUPPORTING', 1

    -- CAL_V_SIT
    UNION ALL SELECT 'CAL_V_SIT', 'VSIT_L1', 'L_SIT', 'L-Sit', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_V_SIT', 'VSIT_L2', 'HANGING_TOES_TO_BAR', 'Hanging Toes-to-Bar', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_V_SIT', 'VSIT_L3', 'V_SIT', 'V-Sit Hold', 'PRIMARY', 1

    -- CAL_DRAGON_FLAG
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 'DF_L1', 'HOLLOW_BODY_ROCK', 'Hollow Body Rock', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 'DF_L2', 'HANGING_LEG_RAISE', 'Hanging Leg Raise', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 'DF_L3', 'DRAGON_FLAG_NEGATIVE', 'Dragon Flag Negative', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 'DF_L3', 'DRAGON_FLAG', 'Dragon Flag', 'PRIMARY', 2
    UNION ALL SELECT 'CAL_DRAGON_FLAG', 'DF_L4', 'FULL_STRICT_DRAGON_FLAG', 'Full Strict Dragon Flag', 'PRIMARY', 1

    -- CAL_FRONT_LEVER_CORE
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 'FLC_L1', 'HOLLOW_BODY_HOLD', 'Hollow Body Hold', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 'FLC_L2', 'FRONT_LEVER_RAISES', 'Front Lever Raises', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 'FLC_L2', 'HANGING_LEG_RAISE', 'Hanging Leg Raise', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_FRONT_LEVER_CORE', 'FLC_L3', 'DRAGON_FLAG', 'Dragon Flag', 'SUPPORTING', 1

    -- CAL_HANDSTAND
    UNION ALL SELECT 'CAL_HANDSTAND', 'HS_L1', 'CHEST_TO_WALL_HANDSTAND', 'Chest-to-Wall Handstand', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HANDSTAND', 'HS_L1', 'FROG_STAND', 'Frog Stand', 'PREREQUISITE', 2
    UNION ALL SELECT 'CAL_HANDSTAND', 'HS_L1', 'PIKE_PUSH_UP', 'Pike Push-Up', 'PREREQUISITE', 3
    UNION ALL SELECT 'CAL_HANDSTAND', 'HS_L2', 'HANDSTAND', 'Handstand', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HANDSTAND', 'HS_L3', 'FREESTANDING_HANDSTAND_BALANCE', 'Freestanding Handstand Balance', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HANDSTAND', 'HS_L4', 'WALL_HANDSTAND_PUSH_UP', 'Wall Handstand Push-Up', 'SUPPORTING', 1

    -- CAL_HUMAN_FLAG
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 'HF_L1', 'SIDE_PLANK_REACH_THROUGH', 'Side Plank Reach-Through', 'PREREQUISITE', 1
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 'HF_L2', 'VERTICAL_FLAG_HOLD', 'Vertical Flag Hold', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 'HF_L3', 'TUCK_HUMAN_FLAG', 'Tuck Human Flag', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HUMAN_FLAG', 'HF_L4', 'HUMAN_FLAG', 'Full Human Flag', 'PRIMARY', 1

    -- Calisthenics Mobility
    UNION ALL SELECT 'CAL_SHOULDER_MOBILITY', 'SM_L1', 'SHOULDER_MOBILITY', 'Shoulder Dislocates (Stick/Band)', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_SHOULDER_MOBILITY', 'SM_L2', 'GERMAN_HANG', 'German Hang', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_SHOULDER_MOBILITY', 'SM_L2', 'SKIN_THE_CAT', 'Skin the Cat', 'SUPPORTING', 2
    UNION ALL SELECT 'CAL_WRIST_MOBILITY', 'WM_L1', 'WRIST_MOBILITY', 'Wrist Conditioning Push-Up Sequence', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_WRIST_MOBILITY', 'WM_L2', 'PLANCHE_LEAN', 'Planche Lean', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_HIP_MOBILITY', 'HM_L1', 'HIP_MOBILITY', '90/90 Hip Internal-External Switches', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_HIP_MOBILITY', 'HM_L2', 'COSSACK_SQUAT', 'Cossack Squat', 'SUPPORTING', 1
    UNION ALL SELECT 'CAL_MIDDLE_SPLIT', 'MS_L2', 'MIDDLE_SPLIT', 'Middle Split Frog Slide', 'PRIMARY', 1
    UNION ALL SELECT 'CAL_FRONT_SPLIT', 'FS_L2', 'FRONT_SPLIT', 'Front Split Slider Hold', 'PRIMARY', 1

    -- GYM SKILLS
    UNION ALL SELECT 'GYM_BENCH_PRESS', 'BP_L2', 'BENCH_PRESS', 'Barbell Bench Press', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_BENCH_PRESS', 'BP_L1', 'DUMBBELL_FLAT_BENCH_PRESS', 'Dumbbell Flat Bench Press', 'PRIMARY', 2
    UNION ALL SELECT 'GYM_BENCH_PRESS', 'BP_L3', 'CLOSE_GRIP_BARBELL_BENCH_PRESS', 'Close-Grip Barbell Bench Press', 'SUPPORTING', 3

    UNION ALL SELECT 'GYM_INCLINE_BENCH_PRESS', 'IBP_L2', 'INCLINE_BENCH_PRESS', 'Incline Barbell Bench Press', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_INCLINE_BENCH_PRESS', 'IBP_L1', 'INCLINE_DUMBBELL_BENCH_PRESS', 'Incline Dumbbell Bench Press', 'PRIMARY', 2

    UNION ALL SELECT 'GYM_OVERHEAD_PRESS', 'OHP_L2', 'OVERHEAD_PRESS', 'Barbell Overhead Press', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_OVERHEAD_PRESS', 'OHP_L1', 'SEATED_DUMBBELL_SHOULDER_PRESS', 'Seated Dumbbell Shoulder Press', 'PRIMARY', 2

    UNION ALL SELECT 'GYM_MACHINE_CHEST_PRESS', 'MCP_L1', 'MACHINE_CHEST_PRESS', 'Seated Machine Chest Press', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_MACHINE_CHEST_PRESS', 'MCP_L3', 'PEC_DECK_MACHINE_FLY', 'Pec Deck Machine Fly', 'SUPPORTING', 2

    UNION ALL SELECT 'GYM_BARBELL_ROW', 'BR_L2', 'BARBELL_ROW', 'Barbell Bent-Over Row', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_BARBELL_ROW', 'BR_L1', 'SINGLE_ARM_DUMBBELL_ROW', 'Single-Arm Dumbbell Row', 'PRIMARY', 2

    UNION ALL SELECT 'GYM_LAT_PULLDOWN', 'LP_L1', 'LAT_PULLDOWN', 'Lat Pulldown (Wide Overhand)', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_LAT_PULLDOWN', 'LP_L2', 'REVERSE_GRIP_LAT_PULLDOWN', 'Reverse Grip Lat Pulldown', 'PRIMARY', 2

    UNION ALL SELECT 'GYM_SEATED_CABLE_ROW', 'SCR_L2', 'SEATED_CABLE_ROW', 'Seated Cable Row (V-Bar)', 'PRIMARY', 1

    UNION ALL SELECT 'GYM_WEIGHTED_PULL_UP', 'WPU_L2', 'WEIGHTED_PULL_UP', 'Weighted Pull-Up', 'PRIMARY', 1

    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 'BS_L2', 'BARBELL_SQUAT', 'Barbell Back Squat', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 'BS_L1', 'GOBLET_SQUAT', 'Goblet Squat', 'PRIMARY', 2
    UNION ALL SELECT 'GYM_BARBELL_SQUAT', 'BS_L2', 'BARBELL_FRONT_SQUAT', 'Barbell Front Squat', 'SUPPORTING', 3

    UNION ALL SELECT 'GYM_ROMANIAN_DEADLIFT', 'RDL_L2', 'ROMANIAN_DEADLIFT', 'Barbell Romanian Deadlift', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_ROMANIAN_DEADLIFT', 'RDL_L1', 'DUMBBELL_ROMANIAN_DEADLIFT', 'Dumbbell Romanian Deadlift', 'PRIMARY', 2

    UNION ALL SELECT 'GYM_LEG_PRESS', 'LPR_L1', 'LEG_PRESS', 'Leg Press (45-Degree)', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_LEG_PRESS', 'LPR_L2', 'HACK_SQUAT_MACHINE', 'Hack Squat Machine', 'PRIMARY', 2

    UNION ALL SELECT 'GYM_LEG_EXTENSION', 'LE_L1', 'LEG_EXTENSION', 'Leg Extension Machine', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_LEG_CURL', 'LC_L1', 'LEG_CURL', 'Seated Leg Curl Machine', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_CALF_RAISE', 'CR_L2', 'CALF_RAISE', 'Standing Machine Calf Raise', 'PRIMARY', 1

    UNION ALL SELECT 'GYM_CABLE_CRUNCH', 'CC_L1', 'CABLE_CRUNCH', 'Cable Crunch (Kneeling Rope)', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_HANGING_LEG_RAISE', 'HLR_L2', 'HANGING_LEG_RAISE', 'Hanging Leg Raise', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_AB_WHEEL', 'AW_L1', 'AB_WHEEL', 'Ab Wheel Rollout (Kneeling)', 'PRIMARY', 1

    UNION ALL SELECT 'GYM_LATERAL_RAISE', 'LR_L1', 'LATERAL_RAISE', 'Dumbbell Lateral Raise', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_BICEP_CURL', 'BC_L2', 'BICEP_CURL', 'Standing Barbell Bicep Curl', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_TRICEPS_EXTENSION', 'TE_L1', 'TRICEPS_EXTENSION', 'Cable Triceps Pushdown (Rope)', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_FACE_PULL', 'FP_L1', 'FACE_PULL', 'Cable Face Pull', 'PRIMARY', 1

    UNION ALL SELECT 'GYM_SHOULDER_MOBILITY', 'GSM_L1', 'GYM_SHOULDER_MOBILITY', 'Standing Kettlebell Halo', 'PRIMARY', 1
    UNION ALL SELECT 'GYM_HIP_ANKLE_MOBILITY', 'GHM_L1', 'GYM_HIP_ANKLE_MOBILITY', 'Banded Ankle Distraction Mobilization', 'PRIMARY', 1
) m
JOIN skills s ON s.code = m.skill_code
JOIN (
    SELECT
        ex_code_or_name_key.id AS exercise_id,
        ex_code_or_name_key.code,
        ex_code_or_name_key.name,
        ex_code_or_name_key.training_type
    FROM exercises ex_code_or_name_key
) matched ON (
    (UPPER(matched.code) = UPPER(m.ex_code) OR LOWER(TRIM(matched.name)) = LOWER(TRIM(m.ex_name)))
    AND (matched.training_type = s.training_type OR matched.training_type = 'BOTH')
)
LEFT JOIN skill_progression_levels spl ON spl.skill_id = s.id AND spl.code = m.level_code
WHERE NOT EXISTS (
    SELECT 1 FROM skill_exercise_links sel
    WHERE sel.skill_id = s.id AND sel.exercise_id = matched.exercise_id
);

-- 8. Backfill exercise_muscle_targets for any newly seeded V59 exercises
INSERT INTO exercise_muscle_targets (exercise_id, muscle_id, role, display_order, created_at)
SELECT
    matched.exercise_id,
    matched.muscle_id,
    CASE WHEN matched.is_primary = 1 THEN 'PRIMARY' ELSE 'SECONDARY' END,
    CASE WHEN matched.is_primary = 1 THEN matched.display_order ELSE 100 + matched.display_order END,
    CURRENT_TIMESTAMP
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
