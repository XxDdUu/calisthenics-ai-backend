ALTER TABLE admin_ai_pending_actions
    ALTER COLUMN program_id DROP NOT NULL;

ALTER TABLE admin_ai_pending_actions
    ADD COLUMN IF NOT EXISTS target_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS action_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_admin_ai_pending_target_user
    ON admin_ai_pending_actions (target_user_id);
