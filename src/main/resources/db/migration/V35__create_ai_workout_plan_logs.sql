-- Migration V35: Create AI Workout Plan Logs and add AI metadata to workout_plans

CREATE TABLE IF NOT EXISTS ai_workout_plan_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100),
    prompt TEXT NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens INT,
    output_tokens INT,
    latency_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_workout_plan_logs_user ON ai_workout_plan_logs(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_workout_plan_logs_idempotency ON ai_workout_plan_logs(idempotency_key);

ALTER TABLE workout_plans
    ADD COLUMN IF NOT EXISTS is_ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_prompt TEXT;
