-- V17: Create personal records and shared PRs for exercise progress tracking and coach collaboration

CREATE TABLE personal_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    workout_session_id BIGINT NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    workout_session_set_id BIGINT REFERENCES workout_session_sets(id) ON DELETE SET NULL,
    record_type VARCHAR(32) NOT NULL,
    record_value NUMERIC(10, 2) NOT NULL,
    secondary_value NUMERIC(10, 2),
    previous_record_value NUMERIC(10, 2),
    previous_secondary_value NUMERIC(10, 2),
    improvement_percentage NUMERIC(6, 2),
    achieved_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_session_exercise_record_type UNIQUE (user_id, workout_session_id, exercise_id, record_type)
);

CREATE INDEX idx_prs_user_exercise ON personal_records(user_id, exercise_id);
CREATE INDEX idx_prs_user_achieved ON personal_records(user_id, achieved_at DESC);
CREATE INDEX idx_prs_session ON personal_records(workout_session_id);

CREATE TABLE shared_personal_records (
    id BIGSERIAL PRIMARY KEY,
    personal_record_id BIGINT NOT NULL REFERENCES personal_records(id) ON DELETE CASCADE,
    client_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    coach_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    coach_feedback TEXT,
    feedback_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'SHARED',
    CONSTRAINT uk_shared_pr_coach UNIQUE (personal_record_id, coach_id)
);

CREATE INDEX idx_shared_prs_coach ON shared_personal_records(coach_id, shared_at DESC);
CREATE INDEX idx_shared_prs_client ON shared_personal_records(client_id, shared_at DESC);

