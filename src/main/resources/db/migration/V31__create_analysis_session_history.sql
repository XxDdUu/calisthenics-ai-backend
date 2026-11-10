-- V31: Create analysis_sessions and rep_histories for persistent Live Analysis history

CREATE TABLE IF NOT EXISTS analysis_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    exercise_type VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_seconds NUMERIC(8, 2) DEFAULT 0.0,
    total_reps INTEGER DEFAULT 0,
    average_form_score NUMERIC(5, 2),
    best_form_score NUMERIC(5, 2),
    average_knee_angle NUMERIC(5, 2),
    deepest_knee_angle NUMERIC(5, 2),
    analysis_status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    camera_view VARCHAR(30) DEFAULT 'SIDE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_analysis_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_analysis_sessions_user_id ON analysis_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_analysis_sessions_user_created ON analysis_sessions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_analysis_sessions_status ON analysis_sessions(analysis_status);

CREATE TABLE IF NOT EXISTS rep_histories (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    rep_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    minimum_knee_angle NUMERIC(5, 2),
    form_score NUMERIC(5, 2),
    depth_rating VARCHAR(30),
    feedback TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rep_histories_session FOREIGN KEY (session_id) REFERENCES analysis_sessions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rep_histories_session_id ON rep_histories(session_id);
CREATE INDEX IF NOT EXISTS idx_rep_histories_session_rep ON rep_histories(session_id, rep_number);
