-- V52: Add membership entitlements for admin-granted PRO and immutable admin PRO audit logs

CREATE TABLE IF NOT EXISTS membership_entitlements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(20) NOT NULL DEFAULT 'PRO',
    grant_source VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    unlimited BOOLEAN NOT NULL DEFAULT TRUE,
    granted_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_membership_entitlement_user_source UNIQUE (user_id, grant_source)
);

CREATE INDEX IF NOT EXISTS idx_membership_entitlement_user_status
    ON membership_entitlements(user_id, status);

CREATE TABLE IF NOT EXISTS admin_pro_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    target_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    admin_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(32) NOT NULL,
    grant_source VARCHAR(30) NOT NULL,
    previous_effective_tier VARCHAR(20) NOT NULL,
    new_effective_tier VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_pro_audit_target_created
    ON admin_pro_audit_logs(target_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_pro_audit_admin_created
    ON admin_pro_audit_logs(admin_user_id, created_at DESC);
