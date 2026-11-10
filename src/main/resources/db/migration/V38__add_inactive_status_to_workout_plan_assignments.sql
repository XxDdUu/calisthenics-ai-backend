-- Assignment deactivation is a soft state transition. Existing rows and session history
-- remain intact; only the allowed status values are expanded.
ALTER TABLE workout_plan_assignments
    DROP CONSTRAINT IF EXISTS workout_plan_assignments_status_check;

ALTER TABLE workout_plan_assignments
    DROP CONSTRAINT IF EXISTS ck_workout_plan_assignments_status;

ALTER TABLE workout_plan_assignments
    ADD CONSTRAINT ck_workout_plan_assignments_status
    CHECK (status IN ('UPCOMING', 'ACTIVE', 'INACTIVE', 'COMPLETED', 'REVOKED', 'ENDED'));

CREATE INDEX IF NOT EXISTS idx_plan_assignments_coach_status
    ON workout_plan_assignments(coach_id, status, updated_at);

CREATE INDEX IF NOT EXISTS idx_plan_assignments_client_status
    ON workout_plan_assignments(client_id, status, updated_at);
