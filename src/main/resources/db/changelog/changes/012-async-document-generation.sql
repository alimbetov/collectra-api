--liquibase formatted sql

--changeset collectra:012-generation-job-runtime
ALTER TABLE generation_jobs
    ADD COLUMN output_formats VARCHAR(30) NOT NULL DEFAULT 'HTML,PDF',
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE generated_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    generation_job_id UUID NOT NULL REFERENCES generation_jobs(id) ON DELETE CASCADE,
    format VARCHAR(10) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_generated_document_job_format UNIQUE (generation_job_id, format),
    CONSTRAINT ck_generated_document_format CHECK (format IN ('HTML','PDF'))
);
CREATE INDEX idx_generated_document_tenant_job
    ON generated_documents(tenant_id, generation_job_id);

--changeset collectra:012-generation-permissions
INSERT INTO permissions(id, code, module, description)
VALUES
    ('10000000-0000-0000-0000-000000000020', 'DOCUMENT_GENERATE', 'document', 'Create document generation jobs'),
    ('10000000-0000-0000-0000-000000000021', 'DOCUMENT_READ', 'document', 'Read document generation jobs and outputs');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code IN ('TENANT_ADMIN', 'TENANT_USER')
  AND permission.code IN ('DOCUMENT_GENERATE', 'DOCUMENT_READ');
