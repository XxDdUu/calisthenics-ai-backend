ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS lowest_form_score NUMERIC(5,2);
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS correct_reps INTEGER DEFAULT 0;
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS incorrect_reps INTEGER DEFAULT 0;
