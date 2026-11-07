-- Add TERMINATED status to coach_client_relationships check constraint
ALTER TABLE coach_client_relationships
    DROP CONSTRAINT IF EXISTS coach_client_relationships_status_check;

ALTER TABLE coach_client_relationships
    ADD CONSTRAINT coach_client_relationships_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED', 'TERMINATED'));
