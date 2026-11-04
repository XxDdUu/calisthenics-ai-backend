-- V20__create_client_body_metrics.sql
-- Client current profile height and historical body measurements

ALTER TABLE client_profiles 
ADD COLUMN IF NOT EXISTS height_cm NUMERIC(5, 1) CHECK (height_cm IS NULL OR (height_cm >= 50.0 AND height_cm <= 300.0));

CREATE TABLE IF NOT EXISTS client_body_measurements (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weight_kg NUMERIC(5, 2) NOT NULL,
    body_fat_percentage NUMERIC(4, 1),
    waist_cm NUMERIC(5, 1),
    chest_cm NUMERIC(5, 1),
    arm_cm NUMERIC(5, 1),
    thigh_cm NUMERIC(5, 1),
    measured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_weight_kg CHECK (weight_kg >= 20.0 AND weight_kg <= 500.0),
    CONSTRAINT chk_body_fat CHECK (body_fat_percentage IS NULL OR (body_fat_percentage >= 0.0 AND body_fat_percentage <= 100.0)),
    CONSTRAINT chk_waist_cm CHECK (waist_cm IS NULL OR (waist_cm >= 20.0 AND waist_cm <= 300.0)),
    CONSTRAINT chk_chest_cm CHECK (chest_cm IS NULL OR (chest_cm >= 20.0 AND chest_cm <= 300.0)),
    CONSTRAINT chk_arm_cm CHECK (arm_cm IS NULL OR (arm_cm >= 10.0 AND arm_cm <= 150.0)),
    CONSTRAINT chk_thigh_cm CHECK (thigh_cm IS NULL OR (thigh_cm >= 15.0 AND thigh_cm <= 200.0))
);

CREATE INDEX IF NOT EXISTS idx_client_body_measurements_client_time 
ON client_body_measurements(client_id, measured_at DESC);
