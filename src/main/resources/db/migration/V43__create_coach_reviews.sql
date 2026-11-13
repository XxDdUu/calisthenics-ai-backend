CREATE TABLE coach_reviews (
    id BIGSERIAL PRIMARY KEY,
    coach_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    overall_rating SMALLINT NOT NULL,
    coaching_quality SMALLINT,
    communication SMALLINT,
    plan_effectiveness SMALLINT,
    support_responsiveness SMALLINT,
    comment VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    moderation_reason VARCHAR(500),
    moderated_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    moderated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coach_reviews_coach_student UNIQUE (coach_id, student_id),
    CONSTRAINT ck_coach_reviews_not_self CHECK (coach_id <> student_id),
    CONSTRAINT ck_coach_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT ck_coach_reviews_overall CHECK (overall_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_coach_reviews_quality CHECK (coaching_quality IS NULL OR coaching_quality BETWEEN 1 AND 5),
    CONSTRAINT ck_coach_reviews_communication CHECK (communication IS NULL OR communication BETWEEN 1 AND 5),
    CONSTRAINT ck_coach_reviews_plan CHECK (plan_effectiveness IS NULL OR plan_effectiveness BETWEEN 1 AND 5),
    CONSTRAINT ck_coach_reviews_support CHECK (support_responsiveness IS NULL OR support_responsiveness BETWEEN 1 AND 5)
);

CREATE INDEX idx_coach_reviews_coach_status_created
    ON coach_reviews(coach_id, status, created_at DESC);
CREATE INDEX idx_coach_reviews_status_created
    ON coach_reviews(status, created_at DESC);

-- Append-only history of every create/edit/moderation event.
CREATE TABLE coach_review_events (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT NOT NULL REFERENCES coach_reviews(id) ON DELETE CASCADE,
    event_type VARCHAR(16) NOT NULL,
    actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    overall_rating SMALLINT NOT NULL,
    comment VARCHAR(1000),
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_coach_review_events_type CHECK (event_type IN ('CREATED', 'EDITED', 'HIDDEN', 'RESTORED'))
);

CREATE INDEX idx_coach_review_events_review_created
    ON coach_review_events(review_id, created_at);
