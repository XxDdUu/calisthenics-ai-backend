-- V60: Exercise Progression Trees (Calisthenics & Gym)
-- Non-destructive schema addition referencing existing exercises(id) without mutating exercise records.

CREATE TABLE IF NOT EXISTS exercise_tree_categories (
    id BIGSERIAL PRIMARY KEY,
    training_type VARCHAR(32) NOT NULL,
    subcategory_key VARCHAR(40) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_exercise_tree_category UNIQUE (training_type, subcategory_key)
);

CREATE INDEX IF NOT EXISTS idx_exercise_tree_categories_type
    ON exercise_tree_categories (training_type, display_order);

CREATE TABLE IF NOT EXISTS exercise_tree_nodes (
    id BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    training_type VARCHAR(32) NOT NULL,
    subcategory_key VARCHAR(40) NOT NULL,
    progression_difficulty VARCHAR(24) NOT NULL,
    difficulty_validated BOOLEAN NOT NULL DEFAULT FALSE,
    tree_tier INTEGER NOT NULL DEFAULT 1,
    branch_key VARCHAR(64) NOT NULL,
    branch_label VARCHAR(120) NOT NULL,
    branch_order INTEGER NOT NULL DEFAULT 0,
    is_primary_placement BOOLEAN NOT NULL DEFAULT TRUE,
    mastery_criteria_type VARCHAR(32) NOT NULL DEFAULT 'COMPLETED_SESSIONS',
    mastery_target_reps INTEGER,
    mastery_target_sets INTEGER,
    mastery_target_hold_seconds INTEGER,
    mastery_target_weight_kg DOUBLE PRECISION,
    mastery_min_completed_sessions INTEGER NOT NULL DEFAULT 3,
    mastery_summary VARCHAR(255),
    needs_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    review_notes TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exercise_tree_node_placement UNIQUE (exercise_id, training_type, subcategory_key)
);

CREATE INDEX IF NOT EXISTS idx_exercise_tree_nodes_type_sub
    ON exercise_tree_nodes (training_type, subcategory_key, tree_tier, branch_order);

CREATE INDEX IF NOT EXISTS idx_exercise_tree_nodes_exercise
    ON exercise_tree_nodes (exercise_id);

CREATE INDEX IF NOT EXISTS idx_exercise_tree_nodes_review
    ON exercise_tree_nodes (needs_manual_review);

CREATE TABLE IF NOT EXISTS exercise_progression_edges (
    id BIGSERIAL PRIMARY KEY,
    source_exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    target_exercise_id BIGINT NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    training_type VARCHAR(32) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    branch_label VARCHAR(120),
    notes TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_exercise_progression_edge UNIQUE (source_exercise_id, target_exercise_id, training_type, relation_type),
    CONSTRAINT chk_exercise_progression_edge_not_self CHECK (source_exercise_id <> target_exercise_id)
);

CREATE INDEX IF NOT EXISTS idx_exercise_progression_edges_source
    ON exercise_progression_edges (source_exercise_id, training_type);

CREATE INDEX IF NOT EXISTS idx_exercise_progression_edges_target
    ON exercise_progression_edges (target_exercise_id, training_type);

CREATE TABLE IF NOT EXISTS exercise_tree_missing_nodes (
    id BIGSERIAL PRIMARY KEY,
    training_type VARCHAR(32) NOT NULL,
    subcategory_key VARCHAR(40) NOT NULL,
    branch_key VARCHAR(64) NOT NULL,
    suggested_name VARCHAR(160) NOT NULL,
    target_difficulty VARCHAR(24) NOT NULL,
    after_exercise_code VARCHAR(80),
    before_exercise_code VARCHAR(80),
    reason TEXT,
    resolved_exercise_id BIGINT REFERENCES exercises(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exercise_tree_missing_node UNIQUE (training_type, subcategory_key, suggested_name)
);

CREATE INDEX IF NOT EXISTS idx_exercise_tree_missing_nodes_type_sub
    ON exercise_tree_missing_nodes (training_type, subcategory_key);
