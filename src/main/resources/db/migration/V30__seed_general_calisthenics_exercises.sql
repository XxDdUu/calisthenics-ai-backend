-- Database Migration V30: Seed General Calisthenics Exercises Catalog
-- Ensures all 10 standard Calisthenics AI exercises exist with consistent codes.

INSERT INTO exercises (name, code, category, difficulty, description, target_muscles, instructions, active)
VALUES
('Push-Up', 'PUSH_UP', 'PUSH', 'BEGINNER',
 'Fundamental upper body horizontal pushing exercise.',
 'Chest, Anterior Deltoids, Triceps, Core',
 'Maintain straight body line, lower until chest is near ground (elbows ~90°), push up to full elbow extension.',
 TRUE),

('Bodyweight Squat', 'SQUAT', 'LEGS', 'BEGINNER',
 'Fundamental lower body compound movement.',
 'Quadriceps, Glutes, Hamstrings, Calves, Core',
 'Keep chest upright, squat down until thighs are parallel or below knees (knees ~90° or deeper), stand up to lockout.',
 TRUE),

('Pull-Up', 'PULL_UP', 'PULL', 'INTERMEDIATE',
 'Fundamental vertical pulling calisthenics exercise.',
 'Lats, Rhomboids, Biceps, Core, Grip',
 'Hang from bar with straight arms, pull body upward until chin passes above the bar, lower with control to dead hang.',
 TRUE),

('Parallel Bar Dips', 'DIPS', 'PUSH', 'BEGINNER',
 'Vertical pushing exercise on parallel bars.',
 'Triceps, Lower Chest, Anterior Deltoids',
 'Lower body until upper arms are parallel to bars (elbows <= 90°), press upward to full lockout.',
 TRUE),

('Handstand', 'HANDSTAND', 'SKILL', 'INTERMEDIATE',
 'Vertical body inversion balance on hands.',
 'Shoulders, Core, Forearms, Traps',
 'Push tall through shoulders, open shoulder angle (180°), maintain hollow body alignment.',
 TRUE),

('Planche', 'PLANCHE', 'SKILL', 'ADVANCED',
 'Isometric hold with straight body supported entirely by hands off the ground.',
 'Anterior Deltoids, Scapula, Triceps, Core, Lower Back',
 'Lock elbows completely (>170°), protract scapula, lean forward over wrists, elevate hips to shoulder height.',
 TRUE),

('Front Lever', 'FRONT_LEVER', 'SKILL', 'ADVANCED',
 'Horizontal body hold hanging from pull-up bar facing ceiling.',
 'Lats, Rear Deltoids, Core, Grip, Forearms',
 'Depress and retract scapula, pull down through straight arms, hold torso and legs horizontal.',
 TRUE),

('Pike Push-Up', 'PIKE_PUSH_UP', 'PUSH', 'INTERMEDIATE',
 'Vertical pushing progression building toward handstand push-up strength.',
 'Anterior Deltoids, Triceps, Upper Chest, Core',
 'Hips elevated at 90° angle, lower head forward forming a tripod with hands, press back up to full lockout.',
 TRUE),

('L-Sit', 'L_SIT', 'CORE', 'INTERMEDIATE',
 'Isometric core and arm support hold with legs extended horizontally at 90 degrees.',
 'Abdominals, Hip Flexors, Triceps, Lats, Forearms',
 'Press floor or parallel bars down with locked arms, depress shoulders, keep legs horizontal and straight.',
 TRUE),

('Muscle-Up', 'MUSCLE_UP', 'PULL', 'ADVANCED',
 'Explosive vertical pull transitioning over the bar into a dip.',
 'Lats, Explosive Pulling, Triceps, Chest, Grip',
 'Pull explosively to upper chest/abs, transition elbows and wrists over the bar, press out to lockout.',
 TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    difficulty = EXCLUDED.difficulty,
    description = EXCLUDED.description,
    target_muscles = EXCLUDED.target_muscles,
    instructions = EXCLUDED.instructions,
    active = TRUE;
