ALTER TABLE exercises
    ADD COLUMN IF NOT EXISTS training_type VARCHAR(32) NOT NULL DEFAULT 'CALISTHENICS',
    ADD COLUMN IF NOT EXISTS secondary_muscles TEXT,
    ADD COLUMN IF NOT EXISTS exercise_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recommended_sets INTEGER,
    ADD COLUMN IF NOT EXISTS recommended_reps VARCHAR(64),
    ADD COLUMN IF NOT EXISTS recommended_rest_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS common_mistakes TEXT,
    ADD COLUMN IF NOT EXISTS safety_considerations TEXT,
    ADD COLUMN IF NOT EXISTS progression_notes TEXT,
    ADD COLUMN IF NOT EXISTS aliases TEXT;

CREATE INDEX IF NOT EXISTS idx_exercises_training_type ON exercises(training_type);
CREATE INDEX IF NOT EXISTS idx_exercises_lower_name ON exercises(LOWER(name));

CREATE TABLE IF NOT EXISTS admin_ai_chat_idempotency (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    conversation_id BIGINT,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_admin_ai_chat_idemp UNIQUE (admin_user_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS bulk_exercise_generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_uid VARCHAR(64) NOT NULL UNIQUE,
    admin_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128),
    conversation_id BIGINT,
    prompt TEXT NOT NULL,
    training_type_filter VARCHAR(32),
    categories_filter TEXT,
    requested_quantity INTEGER NOT NULL,
    batch_size INTEGER NOT NULL DEFAULT 20,
    generated_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    saved_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    processed_batches INTEGER NOT NULL DEFAULT 0,
    total_batches_estimated INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    summary_posted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bulk_ex_gen_admin_idemp UNIQUE (admin_user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_bulk_ex_gen_jobs_admin_created
    ON bulk_exercise_generation_jobs(admin_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS bulk_exercise_generation_items (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES bulk_exercise_generation_jobs(id) ON DELETE CASCADE,
    sequence_number INTEGER,
    exercise_id BIGINT REFERENCES exercises(id) ON DELETE SET NULL,
    exercise_name VARCHAR(120) NOT NULL,
    exercise_code VARCHAR(64),
    training_type VARCHAR(32),
    category VARCHAR(32),
    difficulty VARCHAR(32),
    outcome VARCHAR(32) NOT NULL,
    reason TEXT,
    batch_number INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bulk_ex_gen_items_job_outcome
    ON bulk_exercise_generation_items(job_id, outcome, id ASC);
