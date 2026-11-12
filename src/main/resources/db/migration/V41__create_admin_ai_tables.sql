CREATE TABLE admin_ai_conversations (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL DEFAULT 'New Admin Session',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_ai_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES admin_ai_conversations(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(64),
    metadata TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_ai_recommendations (
    id BIGSERIAL PRIMARY KEY,
    recommendation_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_APPROVAL',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    rationale TEXT,
    payload_json TEXT NOT NULL,
    conversation_id BIGINT REFERENCES admin_ai_conversations(id) ON DELETE SET NULL,
    created_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewed_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    review_notes TEXT,
    created_resource_id BIGINT,
    created_resource_type VARCHAR(40),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_ai_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id BIGINT,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_admin_ai_conv_admin_updated
    ON admin_ai_conversations(admin_user_id, updated_at DESC);

CREATE INDEX idx_admin_ai_msg_conv_created
    ON admin_ai_messages(conversation_id, created_at ASC);

CREATE INDEX idx_admin_ai_rec_status_type
    ON admin_ai_recommendations(status, recommendation_type, created_at DESC);

CREATE INDEX idx_admin_ai_audit_admin_created
    ON admin_ai_audit_logs(admin_user_id, created_at DESC);
