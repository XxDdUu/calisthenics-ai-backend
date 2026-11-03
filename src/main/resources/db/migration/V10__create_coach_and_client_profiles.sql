CREATE TABLE coach_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    bio VARCHAR(500),
    specialties VARCHAR(500),
    years_experience INTEGER CHECK (years_experience IS NULL OR years_experience >= 0),
    training_philosophy VARCHAR(1000),
    coaching_description VARCHAR(2000),
    languages VARCHAR(255),
    certifications VARCHAR(1000),
    social_links VARCHAR(1000),
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    is_accepting_clients BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coach_profiles_user ON coach_profiles(user_id);
CREATE INDEX idx_coach_profiles_public ON coach_profiles(is_public);

CREATE TABLE client_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    bio VARCHAR(500),
    experience_level VARCHAR(50),
    training_experience VARCHAR(500),
    primary_goals VARCHAR(500),
    training_interests VARCHAR(500),
    preferred_frequency VARCHAR(50),
    coaching_language VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_client_profiles_user ON client_profiles(user_id);
