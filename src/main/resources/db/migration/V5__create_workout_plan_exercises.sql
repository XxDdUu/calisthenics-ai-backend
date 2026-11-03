CREATE TABLE workout_plan_exercises (
    id BIGSERIAL PRIMARY KEY,
    workout_plan_day_id BIGINT NOT NULL
        REFERENCES workout_plan_days(id) ON DELETE CASCADE,
    exercise_id BIGINT NOT NULL
        REFERENCES exercises(id) ON DELETE RESTRICT,
    sets INTEGER NOT NULL CHECK (sets > 0),
    reps INTEGER CHECK (reps IS NULL OR reps > 0),
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    rest_seconds INTEGER NOT NULL CHECK (rest_seconds >= 0),
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_workout_plan_exercise_order UNIQUE (workout_plan_day_id, display_order)
);

CREATE INDEX idx_workout_plan_exercises_exercise ON workout_plan_exercises(exercise_id);
