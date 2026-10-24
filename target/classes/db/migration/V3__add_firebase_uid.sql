-- Database Migration V3: Add Firebase UID support and make password_hash optional

ALTER TABLE users ADD COLUMN firebase_uid VARCHAR(128) UNIQUE;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE INDEX idx_users_firebase_uid ON users(firebase_uid);
