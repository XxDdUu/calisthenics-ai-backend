CREATE TABLE workout_plan_assignments (
    id BIGSERIAL PRIMARY KEY,
    relationship_id BIGINT NOT NULL REFERENCES coach_client_relationships(id) ON DELETE RESTRICT,
    workout_plan_id BIGINT NOT NULL REFERENCES workout_plans(id) ON DELETE RESTRICT,
    coach_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    client_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ENDED')),
    start_date DATE NOT NULL,
    end_date DATE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_plan_assignment_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_plan_assignments_client_created
    ON workout_plan_assignments(client_id, created_at);
CREATE INDEX idx_plan_assignments_relationship
    ON workout_plan_assignments(relationship_id);
