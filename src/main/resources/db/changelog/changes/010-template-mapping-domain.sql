--liquibase formatted sql

--changeset collectra:010-template-mapping-domain
CREATE TABLE field_definitions (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants(id),
    field_key VARCHAR(160) NOT NULL,
    label VARCHAR(200) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    category VARCHAR(60) NOT NULL,
    collection BOOLEAN NOT NULL DEFAULT FALSE,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(500),
    example_value VARCHAR(500),
    validation_rules JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_field_type CHECK (data_type IN ('STRING','DECIMAL','INTEGER','DATE','DATETIME','BOOLEAN','OBJECT')),
    CONSTRAINT ck_field_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE UNIQUE INDEX uk_system_field_key ON field_definitions(field_key) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uk_tenant_field_key ON field_definitions(tenant_id, field_key) WHERE tenant_id IS NOT NULL;

CREATE TABLE source_schemas (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    source_format VARCHAR(20) NOT NULL,
    schema_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_source_schema_version UNIQUE (tenant_id, code, schema_version),
    CONSTRAINT ck_source_format CHECK (source_format IN ('EXCEL','CSV','JSON','XML')),
    CONSTRAINT ck_source_schema_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE TABLE source_fields (
    id UUID PRIMARY KEY,
    source_schema_id UUID NOT NULL REFERENCES source_schemas(id) ON DELETE CASCADE,
    source_path VARCHAR(300) NOT NULL,
    detected_type VARCHAR(30),
    sample_value VARCHAR(500),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER,
    CONSTRAINT uk_source_field_path UNIQUE (source_schema_id, source_path)
);

CREATE TABLE mapping_profiles (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    source_schema_id UUID NOT NULL REFERENCES source_schemas(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    profile_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_mapping_profile_version UNIQUE (tenant_id, code, profile_version),
    CONSTRAINT ck_mapping_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE TABLE mapping_rules (
    id UUID PRIMARY KEY,
    mapping_profile_id UUID NOT NULL REFERENCES mapping_profiles(id) ON DELETE CASCADE,
    source_field_id UUID NOT NULL REFERENCES source_fields(id),
    target_field_id UUID NOT NULL REFERENCES field_definitions(id),
    transformation JSONB NOT NULL DEFAULT '{}'::jsonb,
    default_value VARCHAR(500),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_mapping_source UNIQUE (mapping_profile_id, source_field_id)
);

CREATE TABLE document_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_document_template_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_document_template_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE TABLE template_versions (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES document_templates(id) ON DELETE CASCADE,
    template_version INTEGER NOT NULL,
    locale VARCHAR(10) NOT NULL,
    content_html TEXT NOT NULL,
    stylesheet TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_template_version UNIQUE (template_id, template_version, locale),
    CONSTRAINT ck_template_version_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

CREATE TABLE generation_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    document_type VARCHAR(50) NOT NULL,
    mapping_profile_id UUID REFERENCES mapping_profiles(id),
    template_version_id UUID NOT NULL REFERENCES template_versions(id),
    input_file_id UUID,
    normalized_payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step VARCHAR(40),
    error_code VARCHAR(60),
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_generation_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
);
CREATE INDEX idx_generation_jobs_tenant_status ON generation_jobs(tenant_id, status, created_at);
