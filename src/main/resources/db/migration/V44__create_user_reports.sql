CREATE TABLE user_reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reported_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason VARCHAR(40) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    handled_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_reports_not_self CHECK (reporter_id <> reported_user_id),
    CONSTRAINT ck_user_reports_status CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_user_reports_reason CHECK (reason IN (
        'HARASSMENT', 'INAPPROPRIATE_BEHAVIOR', 'SPAM_OR_SCAM', 'ABUSIVE_LANGUAGE',
        'FAKE_ACCOUNT', 'INAPPROPRIATE_PROFILE_CONTENT', 'FRAUD', 'OTHER'))
);

-- One active report per reporter / reported user / reason.
CREATE UNIQUE INDEX uk_user_reports_active_incident
    ON user_reports(reporter_id, reported_user_id, reason)
    WHERE status IN ('PENDING', 'UNDER_REVIEW');
CREATE INDEX idx_user_reports_status_created ON user_reports(status, created_at DESC);
CREATE INDEX idx_user_reports_reported ON user_reports(reported_user_id, created_at DESC);
CREATE INDEX idx_user_reports_reporter_created ON user_reports(reporter_id, created_at DESC);

CREATE TABLE user_report_evidence (
    report_id BIGINT NOT NULL REFERENCES user_reports(id) ON DELETE CASCADE,
    position INT NOT NULL,
    url VARCHAR(500) NOT NULL,
    PRIMARY KEY (report_id, position)
);

-- Audit trail: status changes, internal notes and recorded moderation decisions.
CREATE TABLE user_report_actions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES user_reports(id) ON DELETE CASCADE,
    action_type VARCHAR(24) NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16),
    moderation_action VARCHAR(24),
    suspended_until TIMESTAMP WITH TIME ZONE,
    note VARCHAR(2000),
    admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_report_actions_type CHECK (action_type IN ('STATUS_CHANGE', 'NOTE', 'MODERATION_ACTION')),
    CONSTRAINT ck_user_report_actions_moderation CHECK (moderation_action IS NULL OR moderation_action IN (
        'WARNING', 'TEMPORARY_SUSPENSION', 'PERMANENT_BAN'))
);

CREATE INDEX idx_user_report_actions_report_created ON user_report_actions(report_id, created_at);
