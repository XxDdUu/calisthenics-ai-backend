-- Ensure Front Lever Raises exists in the published exercise catalog with canonical instructions.
INSERT INTO exercises (
    name,
    code,
    category,
    difficulty,
    description,
    target_muscles,
    instructions,
    equipment,
    active,
    status,
    origin,
    training_type,
    recommended_sets,
    recommended_reps,
    recommended_rest_seconds
)
SELECT
    'Front Lever Raises',
    'FRONT_LEVER_RAISES',
    'PULL',
    'INTERMEDIATE',
    'Dynamic straight-arm pulling movement raising the body from active hang through the horizontal Front Lever plane.',
    'Lats, Teres Major, Posterior Deltoids, Core',
    'Start from an active hang with depressed shoulders and locked elbows. Raise your body toward horizontal or inverted position using controlled shoulder extension. Lower slowly with a 3-second eccentric.',
    'Pull-Up Bar or Gymnastic Rings',
    TRUE,
    'PUBLISHED',
    'MANUAL',
    'CALISTHENICS',
    3,
    '10',
    90
WHERE NOT EXISTS (
    SELECT 1 FROM exercises WHERE LOWER(name) = 'front lever raises' AND code <> 'FRONT_LEVER_RAISES'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    instructions = EXCLUDED.instructions,
    active = TRUE,
    status = 'PUBLISHED',
    recommended_sets = COALESCE(exercises.recommended_sets, EXCLUDED.recommended_sets),
    recommended_reps = COALESCE(exercises.recommended_reps, EXCLUDED.recommended_reps),
    recommended_rest_seconds = COALESCE(exercises.recommended_rest_seconds, EXCLUDED.recommended_rest_seconds);

UPDATE exercises
SET instructions = 'Start from an active hang with depressed shoulders and locked elbows. Raise your body toward horizontal or inverted position using controlled shoulder extension. Lower slowly with a 3-second eccentric.',
    active = TRUE,
    status = 'PUBLISHED'
WHERE LOWER(name) = 'front lever raises';
