-- V22: Add media URLs and equipment fields to exercises table
ALTER TABLE exercises
    ADD COLUMN image_url VARCHAR(500),
    ADD COLUMN video_url VARCHAR(500),
    ADD COLUMN equipment VARCHAR(100);
