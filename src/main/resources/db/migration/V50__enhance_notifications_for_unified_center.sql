ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS related_entity_type VARCHAR(64),
    ADD COLUMN IF NOT EXISTS related_entity_id BIGINT,
    ADD COLUMN IF NOT EXISTS deep_link VARCHAR(255),
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(160);

UPDATE notifications
SET related_entity_id = reference_id
WHERE related_entity_id IS NULL
  AND reference_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_created
    ON notifications (recipient_id, is_read, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_recipient_event_key
    ON notifications (recipient_id, event_key)
    WHERE event_key IS NOT NULL;
