-- V33: Add general joint angle columns to support both Squat (knee angle) and Push-up (elbow angle)
ALTER TABLE rep_histories ADD COLUMN IF NOT EXISTS minimum_joint_angle NUMERIC(5, 2);
UPDATE rep_histories SET minimum_joint_angle = minimum_knee_angle WHERE minimum_joint_angle IS NULL AND minimum_knee_angle IS NOT NULL;

ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS average_joint_angle NUMERIC(5, 2);
ALTER TABLE analysis_sessions ADD COLUMN IF NOT EXISTS deepest_joint_angle NUMERIC(5, 2);
UPDATE analysis_sessions SET average_joint_angle = average_knee_angle WHERE average_joint_angle IS NULL AND average_knee_angle IS NOT NULL;
UPDATE analysis_sessions SET deepest_joint_angle = deepest_knee_angle WHERE deepest_joint_angle IS NULL AND deepest_knee_angle IS NOT NULL;
