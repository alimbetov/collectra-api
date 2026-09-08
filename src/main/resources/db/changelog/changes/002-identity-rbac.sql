--liquibase formatted sql

--changeset collectra:002-identity-rbac
ALTER TABLE user_accounts
    ADD COLUMN authorization_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE tenant_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES user_accounts(id),
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_membership_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    module VARCHAR(60) NOT NULL,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id),
    code VARCHAR(80) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_platform_role_code ON roles(code) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uk_tenant_role_code ON roles(tenant_id, code) WHERE tenant_id IS NOT NULL;

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE membership_roles (
    membership_id UUID NOT NULL REFERENCES tenant_memberships(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, role_id)
);

CREATE TABLE platform_user_roles (
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

ALTER TABLE refresh_sessions
    ADD COLUMN membership_id UUID REFERENCES tenant_memberships(id),
    ADD COLUMN replaced_by_id UUID REFERENCES refresh_sessions(id),
    ADD COLUMN reuse_detected_at TIMESTAMPTZ;
CREATE INDEX idx_refresh_family ON refresh_sessions(family_id);

CREATE TABLE service_clients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    client_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    secret_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    token_ttl_seconds INTEGER NOT NULL,
    authorization_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE service_client_scopes (
    service_client_id UUID NOT NULL REFERENCES service_clients(id) ON DELETE CASCADE,
    scope_code VARCHAR(120) NOT NULL,
    PRIMARY KEY (service_client_id, scope_code)
);

CREATE TABLE service_client_ip_rules (
    id UUID PRIMARY KEY,
    service_client_id UUID NOT NULL REFERENCES service_clients(id) ON DELETE CASCADE,
    cidr VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id),
    subject_type VARCHAR(20) NOT NULL,
    subject_id UUID NOT NULL,
    purpose VARCHAR(60) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_otp_subject ON otp_challenges(subject_id, purpose, created_at DESC);

CREATE TABLE security_audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    actor_type VARCHAR(20) NOT NULL,
    actor_id UUID,
    action VARCHAR(100) NOT NULL,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(255),
    trace_id VARCHAR(100),
    correlation_id VARCHAR(100),
    source_ip VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_security_audit_tenant_created
    ON security_audit_events(tenant_id, created_at DESC);

-- Stable IDs make seed data repeatable across environments.
INSERT INTO roles(id, tenant_id, code, scope_type, system_role, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'PLATFORM_SUPER_ADMIN', 'PLATFORM', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000002', NULL, 'TENANT_ADMIN', 'TENANT', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000003', NULL, 'TENANT_USER', 'TENANT', TRUE, now(), now()),
    ('00000000-0000-0000-0000-000000000004', NULL, 'TENANT_TECHNICAL_CLIENT', 'SERVICE', TRUE, now(), now());

INSERT INTO permissions(id, code, module, description)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'USER_READ', 'identity', 'Read tenant users'),
    ('10000000-0000-0000-0000-000000000002', 'USER_INVITE', 'identity', 'Invite tenant users'),
    ('10000000-0000-0000-0000-000000000003', 'USER_UPDATE', 'identity', 'Update tenant users'),
    ('10000000-0000-0000-0000-000000000004', 'USER_BLOCK', 'identity', 'Block tenant users'),
    ('10000000-0000-0000-0000-000000000005', 'ROLE_READ', 'identity', 'Read roles'),
    ('10000000-0000-0000-0000-000000000006', 'ROLE_CREATE', 'identity', 'Create tenant roles'),
    ('10000000-0000-0000-0000-000000000007', 'ROLE_UPDATE', 'identity', 'Update tenant roles'),
    ('10000000-0000-0000-0000-000000000008', 'ROLE_ASSIGN', 'identity', 'Assign tenant roles'),
    ('10000000-0000-0000-0000-000000000009', 'SERVICE_CLIENT_READ', 'integration', 'Read service clients'),
    ('10000000-0000-0000-0000-000000000010', 'SERVICE_CLIENT_CREATE', 'integration', 'Create service clients'),
    ('10000000-0000-0000-0000-000000000011', 'SERVICE_CLIENT_ROTATE_SECRET', 'integration', 'Rotate service secret'),
    ('10000000-0000-0000-0000-000000000012', 'SERVICE_CLIENT_BLOCK', 'integration', 'Block service clients'),
    ('10000000-0000-0000-0000-000000000013', 'AUDIT_READ', 'audit', 'Read tenant security audit');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id FROM permissions;

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id
FROM permissions WHERE code IN ('USER_READ', 'ROLE_READ');

INSERT INTO tenant_memberships(id, tenant_id, user_id, status, joined_at, created_at, updated_at)
SELECT gen_random_uuid(), tenant_id, id, status, created_at, created_at, updated_at
FROM user_accounts;

INSERT INTO membership_roles(membership_id, role_id)
SELECT membership.id,
       CASE account.role
           WHEN 'TENANT_ADMIN' THEN '00000000-0000-0000-0000-000000000002'::uuid
           ELSE '00000000-0000-0000-0000-000000000003'::uuid
       END
FROM tenant_memberships membership
JOIN user_accounts account ON account.id = membership.user_id;

UPDATE refresh_sessions session
SET membership_id = membership.id
FROM tenant_memberships membership
WHERE membership.user_id = session.user_id
  AND membership.tenant_id = session.tenant_id;

-- Expand/contract migration: legacy user_accounts.tenant_id and role remain during 0.2.x.
-- Application code reads TenantMembership; a later verified changeset will drop legacy columns.
