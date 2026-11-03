ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_messages_conversation_client_id
    ON chat_messages (conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

