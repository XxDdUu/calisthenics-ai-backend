-- Flyway V49: Getting Started enrollment lifecycle, day progress tracking, and GETTING_STARTED workout session linkage

ALTER TABLE getting_started_enrollments
    DROP CONSTRAINT IF EXISTS chk_getting_started_enrollment_status;

ALTER TABLE getting_started_enrollments
    ADD COLUMN IF NOT EXISTS added_to_workout_plan BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS start_date DATE;

UPDATE getting_started_enrollments e
SET status = CASE
    WHEN EXISTS (SELECT 1 FROM getting_started_day_completions dc WHERE dc.enrollment_id = e.id)
        THEN 'IN_PROGRESS'
    ELSE 'NOT_STARTED'
END
WHERE e.status = 'ACTIVE';

ALTER TABLE getting_started_enrollments
    ADD CONSTRAINT chk_getting_started_enrollment_status
    CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'ACTIVE', 'COMPLETED', 'DROPPED'));

CREATE TABLE IF NOT EXISTS getting_started_day_progress (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL REFERENCES getting_started_enrollments(id) ON DELETE CASCADE,
    day_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    scheduled_date DATE,
    workout_session_id BIGINT REFERENCES workout_sessions(id) ON DELETE SET NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gs_day_progress_enrollment_day UNIQUE (enrollment_id, day_number),
    CONSTRAINT chk_gs_day_progress_day_number CHECK (day_number BETWEEN 1 AND 3),
    CONSTRAINT chk_gs_day_progress_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX IF NOT EXISTS idx_gs_day_progress_enrollment
    ON getting_started_day_progress(enrollment_id);

-- Backfill per-day progress rows for any pre-existing enrollments
INSERT INTO getting_started_day_progress (enrollment_id, day_number, status, workout_session_id, completed_at, updated_at)
SELECT
    e.id,
    d.day_number,
    CASE WHEN dc.id IS NOT NULL THEN 'COMPLETED' ELSE 'NOT_STARTED' END,
    dc.workout_session_id,
    dc.completed_at,
    CURRENT_TIMESTAMP
FROM getting_started_enrollments e
CROSS JOIN (VALUES (1), (2), (3)) AS d(day_number)
LEFT JOIN getting_started_day_completions dc
    ON dc.enrollment_id = e.id AND dc.day_number = d.day_number
ON CONFLICT (enrollment_id, day_number) DO NOTHING;

ALTER TABLE workout_sessions
    ADD COLUMN IF NOT EXISTS getting_started_enrollment_id BIGINT REFERENCES getting_started_enrollments(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS getting_started_day_number INTEGER;

ALTER TABLE workout_sessions
    DROP CONSTRAINT IF EXISTS chk_workout_sessions_gs_day_number;

ALTER TABLE workout_sessions
    ADD CONSTRAINT chk_workout_sessions_gs_day_number
    CHECK (getting_started_day_number IS NULL OR getting_started_day_number BETWEEN 1 AND 3);

CREATE INDEX IF NOT EXISTS idx_workout_sessions_gs_enrollment
    ON workout_sessions(getting_started_enrollment_id)
    WHERE getting_started_enrollment_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_workout_sessions_active_gs_day
    ON workout_sessions(getting_started_enrollment_id, getting_started_day_number)
    WHERE getting_started_enrollment_id IS NOT NULL AND status IN ('SCHEDULED', 'IN_PROGRESS');
