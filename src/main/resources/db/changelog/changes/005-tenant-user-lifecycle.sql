--liquibase formatted sql

--changeset collectra:005-tenant-user-lifecycle
ALTER TABLE user_accounts
    ADD COLUMN display_name VARCHAR(200),
    ADD COLUMN locale VARCHAR(10),
    ADD COLUMN timezone VARCHAR(60),
    ADD COLUMN email_verified_at TIMESTAMPTZ;

ALTER TABLE refresh_sessions
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN user_agent VARCHAR(512),
    ADD COLUMN source_ip VARCHAR(64);

CREATE INDEX idx_refresh_membership_created
    ON refresh_sessions(membership_id, created_at DESC);

CREATE TABLE user_invitations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(254) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    invited_by UUID NOT NULL REFERENCES user_accounts(id),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);
CREATE INDEX idx_invitation_tenant_email
    ON user_invitations(tenant_id, lower(email), status);

CREATE TABLE user_invitation_roles (
    invitation_id UUID NOT NULL REFERENCES user_invitations(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY(invitation_id, role_id)
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_password_reset_user_created
    ON password_reset_tokens(user_id, created_at DESC);

INSERT INTO permissions(id, code, module, description)
VALUES
    ('10000000-0000-0000-0000-000000000014', 'ROLE_DELETE', 'identity', 'Delete tenant roles'),
    ('10000000-0000-0000-0000-000000000015', 'SERVICE_CLIENT_UPDATE', 'integration', 'Update service clients');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM permissions
WHERE code IN ('ROLE_DELETE', 'SERVICE_CLIENT_UPDATE');
