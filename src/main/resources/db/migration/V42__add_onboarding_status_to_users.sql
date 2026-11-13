-- Database Migration V42: Add onboarding_status column to users table
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_status VARCHAR(30) DEFAULT 'NOT_SHOWN';

-- Existing users registered before this migration are marked as DISMISSED
-- so they won't be shown the beginner onboarding flow
UPDATE users
SET onboarding_status = 'DISMISSED'
WHERE onboarding_status IS NULL;

ALTER TABLE users
    ALTER COLUMN onboarding_status SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_onboarding_status'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_onboarding_status
            CHECK (onboarding_status IN ('NOT_SHOWN', 'COMPACT_BANNER', 'DISMISSED'));
    END IF;
END $$;
