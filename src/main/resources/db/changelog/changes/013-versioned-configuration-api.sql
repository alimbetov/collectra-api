--liquibase formatted sql

--changeset collectra:013-versioned-configuration-parents
CREATE TABLE source_schema_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_source_schema_definition_code UNIQUE (tenant_id, code)
);

CREATE TABLE mapping_profile_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_mapping_profile_definition_code UNIQUE (tenant_id, code)
);

ALTER TABLE source_schemas ADD COLUMN definition_id UUID REFERENCES source_schema_definitions(id);
ALTER TABLE mapping_profiles ADD COLUMN definition_id UUID REFERENCES mapping_profile_definitions(id);
CREATE INDEX idx_source_schema_definition ON source_schemas(definition_id, schema_version DESC);
CREATE INDEX idx_mapping_profile_definition ON mapping_profiles(definition_id, profile_version DESC);

ALTER TABLE source_schemas DROP CONSTRAINT ck_source_schema_status;
ALTER TABLE source_schemas ADD CONSTRAINT ck_source_schema_status
    CHECK (status IN ('DRAFT','VALIDATED','PUBLISHED','ARCHIVED'));
ALTER TABLE mapping_profiles DROP CONSTRAINT ck_mapping_status;
ALTER TABLE mapping_profiles ADD CONSTRAINT ck_mapping_status
    CHECK (status IN ('DRAFT','VALIDATED','PUBLISHED','ARCHIVED'));
ALTER TABLE template_versions DROP CONSTRAINT ck_template_version_status;
ALTER TABLE template_versions ADD CONSTRAINT ck_template_version_status
    CHECK (status IN ('DRAFT','VALIDATED','PUBLISHED','ARCHIVED'));

ALTER TABLE generation_jobs ADD COLUMN source_schema_version_id UUID REFERENCES source_schemas(id);
ALTER TABLE generation_jobs ADD COLUMN mapping_config_sha256 VARCHAR(64);
ALTER TABLE generation_jobs ADD COLUMN template_config_sha256 VARCHAR(64);

--changeset collectra:013-configuration-permissions
INSERT INTO permissions(id, code, module, description) VALUES
    ('10000000-0000-0000-0000-000000000030', 'SOURCE_SCHEMA_READ', 'importing', 'Read source schemas'),
    ('10000000-0000-0000-0000-000000000031', 'SOURCE_SCHEMA_MANAGE', 'importing', 'Manage source schemas'),
    ('10000000-0000-0000-0000-000000000032', 'MAPPING_PROFILE_READ', 'importing', 'Read mapping profiles'),
    ('10000000-0000-0000-0000-000000000033', 'MAPPING_PROFILE_MANAGE', 'importing', 'Manage mapping profiles'),
    ('10000000-0000-0000-0000-000000000034', 'TEMPLATE_READ', 'template', 'Read templates'),
    ('10000000-0000-0000-0000-000000000035', 'TEMPLATE_MANAGE', 'template', 'Manage template drafts'),
    ('10000000-0000-0000-0000-000000000036', 'TEMPLATE_PUBLISH', 'template', 'Publish template versions');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id FROM roles role CROSS JOIN permissions permission
WHERE role.code = 'TENANT_ADMIN'
  AND permission.code IN ('SOURCE_SCHEMA_READ','SOURCE_SCHEMA_MANAGE','MAPPING_PROFILE_READ',
      'MAPPING_PROFILE_MANAGE','TEMPLATE_READ','TEMPLATE_MANAGE','TEMPLATE_PUBLISH');
