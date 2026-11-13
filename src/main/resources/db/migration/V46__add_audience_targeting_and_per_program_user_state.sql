ALTER TABLE getting_started_programs
ADD COLUMN target_audience VARCHAR(30) NOT NULL DEFAULT 'ALL_ATHLETES';

ALTER TABLE getting_started_programs
ADD CONSTRAINT chk_getting_started_target_audience
CHECK (target_audience IN ('ALL_ATHLETES', 'LAST_30_DAYS', 'LAST_14_DAYS'));

CREATE TABLE getting_started_program_user_states (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_id BIGINT NOT NULL REFERENCES getting_started_programs(id) ON DELETE CASCADE,
    program_version INT NOT NULL DEFAULT 1,
    state VARCHAR(30) NOT NULL DEFAULT 'NOT_SHOWN',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_program_user_state UNIQUE (user_id, program_id, program_version),
    CONSTRAINT chk_program_user_state CHECK (state IN ('NOT_SHOWN', 'COMPACT_BANNER', 'DISMISSED'))
);

CREATE INDEX idx_program_user_state_user ON getting_started_program_user_states(user_id);
