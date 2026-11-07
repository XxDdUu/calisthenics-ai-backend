-- V28: Enhance analyses table with video metadata and biomechanical report fields
-- Extends the existing analyses table without destroying any existing data.

ALTER TABLE analyses
    -- Original video metadata (never inferred from sampled frames)
    ADD COLUMN IF NOT EXISTS video_duration_ms        BIGINT,
    ADD COLUMN IF NOT EXISTS video_width              INTEGER,
    ADD COLUMN IF NOT EXISTS video_height             INTEGER,
    ADD COLUMN IF NOT EXISTS original_fps             NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS total_frames             INTEGER,
    ADD COLUMN IF NOT EXISTS analyzed_frames          INTEGER,
    ADD COLUMN IF NOT EXISTS sampling_fps             NUMERIC(6, 2),
    ADD COLUMN IF NOT EXISTS file_size_bytes          BIGINT,
    ADD COLUMN IF NOT EXISTS processing_time_ms       NUMERIC(10, 2),
    -- Biomechanical aggregates stored as JSON blobs (avoids table explosion at current scale)
    ADD COLUMN IF NOT EXISTS component_scores_json    TEXT,
    ADD COLUMN IF NOT EXISTS stability_json           TEXT,
    ADD COLUMN IF NOT EXISTS timeline_json            TEXT;

-- Index to allow fast lookup of video analyses with metadata
CREATE INDEX IF NOT EXISTS idx_analyses_media_type ON analyses(media_type);
