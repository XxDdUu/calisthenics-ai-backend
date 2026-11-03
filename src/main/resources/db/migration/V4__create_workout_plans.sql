CREATE TABLE workout_plans (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 100),
    split_type VARCHAR(20) NOT NULL CHECK (split_type IN ('PPL', 'UPPER_LOWER', 'FULL_BODY')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workout_plans_user_created ON workout_plans(user_id, created_at);

CREATE TABLE workout_plan_days (
    id BIGSERIAL PRIMARY KEY,
    workout_plan_id BIGINT NOT NULL REFERENCES workout_plans(id) ON DELETE CASCADE,
    day_of_week VARCHAR(10) NOT NULL CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    session_type VARCHAR(10) NOT NULL CHECK (
        session_type IN ('PUSH', 'PULL', 'LEGS', 'UPPER', 'LOWER', 'FULL_BODY', 'REST')
    ),
    CONSTRAINT uk_workout_plan_day_weekday UNIQUE (workout_plan_id, day_of_week)
);
