CREATE TABLE ai_workout_plan_change_previews (
    id BIGSERIAL PRIMARY KEY,
    preview_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id BIGINT NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    workout_plan_id BIGINT NOT NULL REFERENCES workout_plans(id) ON DELETE CASCADE,
    assistant_message_id BIGINT REFERENCES ai_messages(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    revision INTEGER NOT NULL DEFAULT 1,
    summary TEXT NOT NULL,
    source_prompt TEXT,
    plan_fingerprint VARCHAR(128) NOT NULL,
    operations_json TEXT NOT NULL,
    preview_payload_json TEXT NOT NULL,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_wp_previews_user_conv_status
    ON ai_workout_plan_change_previews(user_id, conversation_id, status, created_at DESC);

CREATE INDEX idx_ai_wp_previews_preview_id_user
    ON ai_workout_plan_change_previews(preview_id, user_id);
