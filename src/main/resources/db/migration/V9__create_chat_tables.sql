CREATE TABLE chat_conversations (
    id BIGSERIAL PRIMARY KEY,
    coach_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    coach_last_read_at TIMESTAMP WITH TIME ZONE,
    client_last_read_at TIMESTAMP WITH TIME ZONE,
    last_message_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_participants_not_same CHECK (coach_id <> client_id),
    CONSTRAINT uk_chat_conversation_pair UNIQUE (coach_id, client_id)
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content VARCHAR(4000) NOT NULL,
    message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_chat_message_content_non_blank CHECK (length(trim(content)) > 0)
);

CREATE INDEX idx_chat_conversation_coach
    ON chat_conversations(coach_id, updated_at);
CREATE INDEX idx_chat_conversation_client
    ON chat_conversations(client_id, updated_at);
CREATE INDEX idx_chat_conversation_last_message
    ON chat_conversations(last_message_at);
CREATE INDEX idx_chat_message_conversation_created
    ON chat_messages(conversation_id, created_at);
CREATE INDEX idx_chat_message_sender
    ON chat_messages(sender_id, created_at);
