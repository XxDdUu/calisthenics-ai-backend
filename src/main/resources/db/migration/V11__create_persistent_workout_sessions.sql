ALTER TABLE workout_sessions
    ADD COLUMN workout_plan_id BIGINT REFERENCES workout_plans(id) ON DELETE SET NULL,
    ADD COLUMN workout_plan_day_id BIGINT REFERENCES workout_plan_days(id) ON DELETE SET NULL,
    ADD COLUMN workout_plan_assignment_id BIGINT REFERENCES workout_plan_assignments(id) ON DELETE SET NULL,
    ADD COLUMN workout_date DATE,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN last_saved_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN duration_seconds BIGINT,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_workout_sessions_user_status ON workout_sessions(user_id, status);
CREATE INDEX idx_workout_sessions_user_date ON workout_sessions(user_id, workout_date);
CREATE UNIQUE INDEX uk_active_session_for_scheduled_day
    ON workout_sessions(user_id, workout_plan_day_id, workout_date)
    WHERE status = 'IN_PROGRESS' AND workout_plan_day_id IS NOT NULL AND workout_date IS NOT NULL;

CREATE TABLE workout_session_exercises (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    workout_session_id BIGINT NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id BIGINT REFERENCES exercises(id) ON DELETE SET NULL,
    exercise_name_snapshot VARCHAR(255) NOT NULL,
    instructions_snapshot TEXT,
    plan_notes_snapshot TEXT,
    order_index INT NOT NULL,
    target_sets INT NOT NULL CHECK (target_sets > 0),
    target_reps INT CHECK (target_reps IS NULL OR target_reps >= 0),
    target_hold_duration_seconds INT CHECK (target_hold_duration_seconds IS NULL OR target_hold_duration_seconds >= 0),
    rest_seconds INT NOT NULL CHECK (rest_seconds >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    CONSTRAINT uk_workout_session_exercise_order UNIQUE (workout_session_id, order_index)
);

CREATE INDEX idx_session_exercises_exercise ON workout_session_exercises(exercise_id);

CREATE TABLE workout_session_sets (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    workout_session_exercise_id BIGINT NOT NULL REFERENCES workout_session_exercises(id) ON DELETE CASCADE,
    set_number INT NOT NULL CHECK (set_number > 0),
    reps INT CHECK (reps IS NULL OR reps BETWEEN 0 AND 10000),
    hold_duration_seconds INT CHECK (hold_duration_seconds IS NULL OR hold_duration_seconds BETWEEN 0 AND 86400),
    weight_kg NUMERIC(7, 2) CHECK (weight_kg IS NULL OR weight_kg BETWEEN 0 AND 2000),
    assistance_weight_kg NUMERIC(7, 2) CHECK (assistance_weight_kg IS NULL OR assistance_weight_kg BETWEEN 0 AND 2000),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    CONSTRAINT uk_session_exercise_set_number UNIQUE (workout_session_exercise_id, set_number)
);
