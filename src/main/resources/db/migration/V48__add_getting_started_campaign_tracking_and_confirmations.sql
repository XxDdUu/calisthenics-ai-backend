CREATE TABLE getting_started_campaign_events (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL REFERENCES getting_started_programs(id) ON DELETE CASCADE,
    program_version INTEGER NOT NULL DEFAULT 1,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_gs_campaign_events_program_type
    ON getting_started_campaign_events(program_id, event_type);

CREATE INDEX idx_gs_campaign_events_prog_user_type
    ON getting_started_campaign_events(program_id, user_id, event_type);

CREATE TABLE admin_ai_pending_actions (
    id BIGSERIAL PRIMARY KEY,
    confirmation_token VARCHAR(64) NOT NULL UNIQUE,
    admin_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id BIGINT REFERENCES admin_ai_conversations(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    program_id BIGINT NOT NULL REFERENCES getting_started_programs(id) ON DELETE CASCADE,
    target_audience VARCHAR(30),
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_ai_pending_admin_conv_status
    ON admin_ai_pending_actions(admin_user_id, conversation_id, status, created_at DESC);

ALTER TABLE admin_ai_conversations
    ADD COLUMN active_getting_started_program_id BIGINT REFERENCES getting_started_programs(id) ON DELETE SET NULL;
