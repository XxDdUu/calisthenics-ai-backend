-- V36: Extend analysis_sessions and rep_histories for comprehensive live analysis workout summaries & AI recommendations

ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS exercise_id BIGINT;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS overall_score NUMERIC(5, 2);
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS analysis_confidence NUMERIC(4, 3);
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS peak_hold_duration_seconds NUMERIC(8, 2);
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS exercise_metrics_json TEXT;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS detected_errors_json TEXT;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS ai_summary TEXT;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS ai_recommendations_json TEXT;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS ai_summary_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS original_video_url TEXT;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS annotated_video_url TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_analysis_sessions_exercise'
    ) THEN
        ALTER TABLE analysis_sessions
            ADD CONSTRAINT fk_analysis_sessions_exercise
            FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_analysis_sessions_exercise_id ON analysis_sessions(exercise_id);

ALTER TABLE rep_histories ADD COLUMN IF NOT EXISTS timestamp_offset_ms BIGINT;
ALTER TABLE rep_histories ADD COLUMN IF NOT EXISTS completion_status VARCHAR(30);
ALTER TABLE rep_histories ADD COLUMN IF NOT EXISTS detected_errors_json TEXT;
ALTER TABLE rep_histories ADD COLUMN IF NOT EXISTS rep_metrics_json TEXT;
