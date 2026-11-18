ALTER TABLE exercises
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS origin VARCHAR(32) NOT NULL DEFAULT 'MANUAL';

UPDATE exercises
SET status = CASE WHEN active = TRUE THEN 'PUBLISHED' ELSE 'DRAFT' END;

UPDATE exercises e
SET origin = 'AI_RECOMMENDED'
WHERE EXISTS (
    SELECT 1 FROM admin_ai_recommendations r
    WHERE r.created_resource_type = 'EXERCISE'
      AND r.created_resource_id = e.id
);

CREATE INDEX IF NOT EXISTS idx_exercises_status ON exercises(status);
CREATE INDEX IF NOT EXISTS idx_exercises_origin ON exercises(origin);
