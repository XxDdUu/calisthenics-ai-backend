-- Database Migration V23: Create Registration OTPs Table
CREATE TABLE registration_otps (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    experience_level VARCHAR(50) DEFAULT 'INTERMEDIATE',
    otp_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_registration_otp_attempts CHECK (attempts >= 0 AND attempts <= 5)
);

CREATE INDEX idx_registration_otp_email_created
    ON registration_otps (email, created_at DESC);

