-- Day display orders are updated as a complete permutation. Deferring the unique constraint
-- allows swaps without parking rows at values that violate the 1..7 check constraint.
ALTER TABLE workout_plan_days
    DROP CONSTRAINT uk_workout_plan_day_display_order;

ALTER TABLE workout_plan_days
    ADD CONSTRAINT uk_workout_plan_day_display_order
        UNIQUE (workout_plan_id, display_order)
        DEFERRABLE INITIALLY DEFERRED;
