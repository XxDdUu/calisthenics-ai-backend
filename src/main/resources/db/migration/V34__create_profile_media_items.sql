CREATE TABLE profile_media_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_type VARCHAR(20) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    thumbnail_url VARCHAR(2048),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_profile_media_user_type_order
    ON profile_media_items (user_id, profile_type, display_order, id);
