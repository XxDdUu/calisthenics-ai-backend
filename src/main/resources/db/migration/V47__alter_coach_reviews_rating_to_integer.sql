ALTER TABLE coach_reviews
    ALTER COLUMN overall_rating TYPE INTEGER,
    ALTER COLUMN coaching_quality TYPE INTEGER,
    ALTER COLUMN communication TYPE INTEGER,
    ALTER COLUMN plan_effectiveness TYPE INTEGER,
    ALTER COLUMN support_responsiveness TYPE INTEGER;

ALTER TABLE coach_review_events
    ALTER COLUMN overall_rating TYPE INTEGER;
