--liquibase formatted sql

--changeset collectra:006-platform-identity
ALTER TABLE user_accounts ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN tenant_id DROP NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN membership_id DROP NOT NULL;

ALTER TABLE refresh_sessions
    ADD COLUMN context_type VARCHAR(20) NOT NULL DEFAULT 'TENANT';

ALTER TABLE refresh_sessions
    ADD CONSTRAINT ck_refresh_context CHECK (
        (context_type = 'TENANT' AND tenant_id IS NOT NULL AND membership_id IS NOT NULL)
        OR
        (context_type = 'PLATFORM' AND tenant_id IS NULL AND membership_id IS NULL)
    );

CREATE UNIQUE INDEX uk_platform_user_email
    ON user_accounts (lower(email)) WHERE tenant_id IS NULL;

CREATE INDEX idx_refresh_membership_active
    ON refresh_sessions (membership_id, created_at DESC)
    WHERE revoked_at IS NULL;
