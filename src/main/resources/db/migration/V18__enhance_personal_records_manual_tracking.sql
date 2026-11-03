-- V18: Enhance personal records table for manual tracking, custom exercises, units, and notes

ALTER TABLE personal_records ALTER COLUMN workout_session_id DROP NOT NULL;
ALTER TABLE personal_records ALTER COLUMN exercise_id DROP NOT NULL;

ALTER TABLE personal_records ADD COLUMN IF NOT EXISTS unit VARCHAR(32);
ALTER TABLE personal_records ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE personal_records ADD COLUMN IF NOT EXISTS custom_exercise_name VARCHAR(255);
ALTER TABLE personal_records ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_prs_user_type ON personal_records(user_id, record_type);

