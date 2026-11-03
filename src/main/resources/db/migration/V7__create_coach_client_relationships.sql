CREATE TABLE coach_client_relationships (
    id BIGSERIAL PRIMARY KEY,
    coach_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_coach_client_not_self CHECK (coach_id <> client_id)
);

CREATE INDEX idx_coach_client_relationship_coach
    ON coach_client_relationships(coach_id, status);
CREATE INDEX idx_coach_client_relationship_client
    ON coach_client_relationships(client_id, status);

CREATE UNIQUE INDEX uk_coach_client_pending_pair
    ON coach_client_relationships(coach_id, client_id)
    WHERE status = 'PENDING';
CREATE UNIQUE INDEX uk_coach_client_active_pair
    ON coach_client_relationships(coach_id, client_id)
    WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uk_client_single_active_coach
    ON coach_client_relationships(client_id)
    WHERE status = 'ACTIVE';
