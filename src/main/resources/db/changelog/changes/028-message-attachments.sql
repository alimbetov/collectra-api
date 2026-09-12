--liquibase formatted sql

--changeset collectra:028-message-attachments
ALTER TABLE messages
    ADD COLUMN delivery_requested_at TIMESTAMPTZ;

CREATE TABLE message_attachments (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    generation_job_id UUID NOT NULL REFERENCES generation_jobs(id),
    generated_document_id UUID REFERENCES generated_documents(id),
    output_format VARCHAR(20) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(80),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    ready_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,

    CONSTRAINT uk_message_attachment_job UNIQUE (message_id, generation_job_id),
    CONSTRAINT ck_message_attachment_format CHECK (output_format IN ('PDF')),
    CONSTRAINT ck_message_attachment_status CHECK (status IN ('PENDING','READY','FAILED')),
    CONSTRAINT ck_message_attachment_state CHECK (
        (status = 'PENDING' AND generated_document_id IS NULL AND ready_at IS NULL AND failed_at IS NULL)
        OR
        (status = 'READY' AND generated_document_id IS NOT NULL AND ready_at IS NOT NULL AND failed_at IS NULL)
        OR
        (status = 'FAILED' AND generated_document_id IS NULL AND ready_at IS NULL AND failed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_message_attachment_document
    ON message_attachments(message_id, generated_document_id)
    WHERE generated_document_id IS NOT NULL;

CREATE INDEX idx_message_attachments_tenant_message
    ON message_attachments(tenant_id, message_id);

CREATE INDEX idx_message_attachments_tenant_job
    ON message_attachments(tenant_id, generation_job_id);
