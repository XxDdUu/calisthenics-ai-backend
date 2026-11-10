-- Database Migration V37: Add nullable gender column to users table
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_gender'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_gender
            CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'));
    END IF;
END $$;
