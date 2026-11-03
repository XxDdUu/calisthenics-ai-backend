CREATE TABLE password_reset_otps (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    verified_at TIMESTAMP WITH TIME ZONE,
    reset_token_hash VARCHAR(64),
    reset_expires_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_password_reset_otp_attempts CHECK (attempts >= 0 AND attempts <= 5)
);

CREATE INDEX idx_password_reset_otp_user_created
    ON password_reset_otps (user_id, created_at DESC);

CREATE INDEX idx_password_reset_otp_reset_token
    ON password_reset_otps (reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;
