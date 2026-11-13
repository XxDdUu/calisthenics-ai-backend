CREATE TABLE getting_started_programs (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    version INT NOT NULL DEFAULT 1,
    creator_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE TABLE getting_started_program_days (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES getting_started_programs(id) ON DELETE CASCADE,
    day_number INT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    CONSTRAINT uk_getting_started_day UNIQUE (program_id, day_number),
    CONSTRAINT chk_getting_started_day_number CHECK (day_number BETWEEN 1 AND 3)
);

CREATE TABLE getting_started_program_exercises (
    id BIGSERIAL PRIMARY KEY,
    program_day_id BIGINT NOT NULL REFERENCES getting_started_program_days(id) ON DELETE CASCADE,
    exercise_id BIGINT REFERENCES exercises(id) ON DELETE SET NULL,
    exercise_name VARCHAR(150) NOT NULL,
    sets INT NOT NULL DEFAULT 3,
    reps INT,
    duration_seconds INT,
    rest_seconds INT NOT NULL DEFAULT 60,
    instructions TEXT,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_getting_started_exercise_order UNIQUE (program_day_id, display_order)
);

CREATE TABLE getting_started_enrollments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_id BIGINT NOT NULL REFERENCES getting_started_programs(id) ON DELETE CASCADE,
    program_version INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_getting_started_user_program UNIQUE (user_id, program_id),
    CONSTRAINT chk_getting_started_enrollment_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'DROPPED'))
);

CREATE TABLE getting_started_day_completions (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL REFERENCES getting_started_enrollments(id) ON DELETE CASCADE,
    day_number INT NOT NULL,
    workout_session_id BIGINT REFERENCES workout_sessions(id) ON DELETE SET NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_getting_started_day_completion UNIQUE (enrollment_id, day_number),
    CONSTRAINT chk_getting_started_completion_day CHECK (day_number BETWEEN 1 AND 3)
);

CREATE INDEX idx_getting_started_programs_published ON getting_started_programs(published);
CREATE INDEX idx_getting_started_enrollments_user ON getting_started_enrollments(user_id);
