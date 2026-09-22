--liquibase formatted sql

--changeset collectra:040-message-document-links
ALTER TABLE campaigns
    ADD COLUMN document_template_version_id UUID,
    ADD COLUMN generated_pdf_link BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE message_document_links (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    generation_job_id UUID NOT NULL REFERENCES generation_jobs(id),
    generated_document_id UUID REFERENCES generated_documents(id),
    output_format VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(80),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    CONSTRAINT uk_message_document_link_job UNIQUE(message_id, generation_job_id),
    CONSTRAINT uk_message_document_link_token UNIQUE(token_hash),
    CONSTRAINT ck_message_document_link_format CHECK(output_format IN ('PDF')),
    CONSTRAINT ck_message_document_link_status CHECK(status IN ('PENDING','READY','FAILED'))
);

CREATE INDEX idx_message_document_links_tenant_message
    ON message_document_links(tenant_id, message_id);

CREATE INDEX idx_message_document_links_tenant_job
    ON message_document_links(tenant_id, generation_job_id);
