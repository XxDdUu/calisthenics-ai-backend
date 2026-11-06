-- Add a flexible weekly split structure without replacing legacy plan/exercise data.
-- Existing plan rows become active according to their most recently updated plan per user.

ALTER TABLE workout_plans
    DROP CONSTRAINT IF EXISTS workout_plans_split_type_check;

ALTER TABLE workout_plans
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE workout_plan_days
    DROP CONSTRAINT IF EXISTS workout_plan_days_session_type_check;

ALTER TABLE workout_plan_days
    ALTER COLUMN session_type DROP NOT NULL;

ALTER TABLE workout_plan_days
    ALTER COLUMN session_type TYPE VARCHAR(30);

ALTER TABLE workout_plan_days
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS is_rest_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS display_order INTEGER;

-- Preserve prior REST semantics and give existing rows the canonical Monday-to-Sunday order.
UPDATE workout_plan_days
SET is_rest_day = TRUE
WHERE session_type = 'REST';

UPDATE workout_plan_days
SET display_order = CASE day_of_week
    WHEN 'MONDAY' THEN 1
    WHEN 'TUESDAY' THEN 2
    WHEN 'WEDNESDAY' THEN 3
    WHEN 'THURSDAY' THEN 4
    WHEN 'FRIDAY' THEN 5
    WHEN 'SATURDAY' THEN 6
    WHEN 'SUNDAY' THEN 7
END
WHERE display_order IS NULL;

ALTER TABLE workout_plan_days
    ALTER COLUMN display_order SET NOT NULL;

ALTER TABLE workout_plan_days
    ADD CONSTRAINT ck_workout_plan_day_display_order
        CHECK (display_order BETWEEN 1 AND 7);

ALTER TABLE workout_plan_days
    ADD CONSTRAINT uk_workout_plan_day_display_order
        UNIQUE (workout_plan_id, display_order);

WITH ranked_plans AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id
               ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST, id DESC
           ) AS rank
    FROM workout_plans
)
UPDATE workout_plans plan
SET is_active = TRUE
FROM ranked_plans ranked
WHERE plan.id = ranked.id
  AND ranked.rank = 1;

-- This is the database-level backstop for the transactional activation service.
CREATE UNIQUE INDEX IF NOT EXISTS uk_workout_plans_one_active_per_user
    ON workout_plans (user_id)
    WHERE is_active = TRUE;
