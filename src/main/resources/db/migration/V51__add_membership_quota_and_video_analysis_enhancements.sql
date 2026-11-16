-- V51: Add membership tier to users, daily video analysis quota tracking, and v1.0 video_analyses fields

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS membership_tier VARCHAR(20) NOT NULL DEFAULT 'FREE';

CREATE TABLE IF NOT EXISTS video_analysis_quota_usages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    quota_date DATE NOT NULL,
    used_count INTEGER NOT NULL DEFAULT 0,
    daily_limit INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_video_quota_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_video_quota_user_date UNIQUE (user_id, quota_date)
);

CREATE INDEX IF NOT EXISTS idx_video_quota_user_date
    ON video_analysis_quota_usages(user_id, quota_date);

ALTER TABLE video_analyses
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(20) DEFAULT '1.0',
    ADD COLUMN IF NOT EXISTS exercise_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS rep_count INTEGER,
    ADD COLUMN IF NOT EXISTS valid_reps INTEGER,
    ADD COLUMN IF NOT EXISTS incomplete_reps INTEGER,
    ADD COLUMN IF NOT EXISTS reps_json TEXT,
    ADD COLUMN IF NOT EXISTS movement_metrics_json TEXT,
    ADD COLUMN IF NOT EXISTS issues_json TEXT,
    ADD COLUMN IF NOT EXISTS recommendations_json TEXT,
    ADD COLUMN IF NOT EXISTS camera_quality_json TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_video_analyses_user_idempotency
    ON video_analyses(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
