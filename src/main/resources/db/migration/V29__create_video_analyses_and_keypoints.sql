-- V29: Create video_analyses and video_analysis_keypoints tables for AI video scoring

CREATE TABLE IF NOT EXISTS video_analyses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    exercise_id BIGINT,
    original_video_key VARCHAR(255) NOT NULL,
    annotated_video_key VARCHAR(255),
    overall_score NUMERIC(5, 2),
    form_accuracy NUMERIC(5, 2),
    stability_score NUMERIC(5, 2),
    alignment_score NUMERIC(5, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    duration_ms BIGINT,
    width INTEGER,
    height INTEGER,
    fps NUMERIC(6, 2),
    metrics_json TEXT,
    feedback_json TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_video_analyses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_video_analyses_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_video_analyses_user_id ON video_analyses(user_id);
CREATE INDEX IF NOT EXISTS idx_video_analyses_status ON video_analyses(status);
CREATE INDEX IF NOT EXISTS idx_video_analyses_created_at ON video_analyses(created_at DESC);

CREATE TABLE IF NOT EXISTS video_analysis_keypoints (
    id BIGSERIAL PRIMARY KEY,
    video_analysis_id BIGINT NOT NULL,
    frame_index INTEGER NOT NULL,
    timestamp_ms BIGINT NOT NULL,
    keypoints_json JSONB NOT NULL,
    CONSTRAINT fk_video_analysis_keypoints_analysis FOREIGN KEY (video_analysis_id) REFERENCES video_analyses(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_video_analysis_keypoints_analysis_id ON video_analysis_keypoints(video_analysis_id);
CREATE INDEX IF NOT EXISTS idx_video_analysis_keypoints_frame_idx ON video_analysis_keypoints(video_analysis_id, frame_index);
CREATE INDEX IF NOT EXISTS idx_video_analysis_keypoints_timestamp ON video_analysis_keypoints(video_analysis_id, timestamp_ms);
