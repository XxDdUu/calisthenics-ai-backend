CREATE TABLE exercise_media_items (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    media_type VARCHAR(16) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    thumbnail_url VARCHAR(2048),
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_exercise_media_items_exercise_order
    ON exercise_media_items (exercise_id, display_order, id);
