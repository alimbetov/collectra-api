--liquibase formatted sql

--changeset collectra:045-integration-source-foundation
CREATE TABLE integration_sources (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    service_client_id UUID NOT NULL REFERENCES service_clients(id),
    source_schema_definition_id UUID NOT NULL REFERENCES source_schema_definitions(id),
    mapping_profile_definition_id UUID NOT NULL REFERENCES mapping_profile_definitions(id),
    processing_mode VARCHAR(30) NOT NULL,
    header_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    resource_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    routing_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_integration_source_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_integration_source_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','ARCHIVED'))
);
CREATE INDEX idx_integration_source_tenant_status ON integration_sources(tenant_id, status, code);
CREATE INDEX idx_integration_source_client ON integration_sources(service_client_id);

--changeset collectra:045-integration-source-permissions
INSERT INTO permissions(id, code, module, description) VALUES
    ('10000000-0000-0000-0000-000000000090', 'INTEGRATION_SOURCE_READ', 'integration', 'Read integration sources'),
    ('10000000-0000-0000-0000-000000000091', 'INTEGRATION_SOURCE_MANAGE', 'integration', 'Manage integration sources'),
    ('10000000-0000-0000-0000-000000000092', 'INGESTION_READ', 'integration', 'Read integration ingestion activity');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role CROSS JOIN permissions permission
WHERE role.code = 'TENANT_ADMIN'
  AND permission.code IN ('INTEGRATION_SOURCE_READ','INTEGRATION_SOURCE_MANAGE','INGESTION_READ');
