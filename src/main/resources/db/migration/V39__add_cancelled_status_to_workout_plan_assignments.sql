-- Relationship termination cancels non-terminal assignments using CANCELLED while
-- preserving assignment rows and completed workout history.
ALTER TABLE workout_plan_assignments
    DROP CONSTRAINT IF EXISTS workout_plan_assignments_status_check;

ALTER TABLE workout_plan_assignments
    DROP CONSTRAINT IF EXISTS ck_workout_plan_assignments_status;

ALTER TABLE workout_plan_assignments
    ADD CONSTRAINT ck_workout_plan_assignments_status
    CHECK (status IN ('UPCOMING', 'ACTIVE', 'INACTIVE', 'COMPLETED', 'CANCELLED', 'REVOKED', 'ENDED'));

UPDATE workout_plan_assignments wpa
SET status = 'CANCELLED',
    updated_at = CURRENT_TIMESTAMP
FROM coach_client_relationships r
WHERE wpa.relationship_id = r.id
  AND r.status IN ('TERMINATED', 'CANCELLED')
  AND wpa.status IN ('ACTIVE', 'INACTIVE', 'UPCOMING', 'ENDED');
