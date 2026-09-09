--liquibase formatted sql

--changeset collectra:014-row-aware-source-schema
ALTER TABLE source_fields
    ADD COLUMN scope VARCHAR(30) NOT NULL DEFAULT 'DOCUMENT',
    ADD COLUMN document_key BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN value_policy VARCHAR(30) NOT NULL DEFAULT 'FIRST_NON_EMPTY';
ALTER TABLE source_fields ADD CONSTRAINT ck_source_field_scope
    CHECK (scope IN ('DOCUMENT','ITEM','ROW_CONTROL','IGNORE'));
ALTER TABLE source_fields ADD CONSTRAINT ck_source_field_value_policy
    CHECK (value_policy IN ('FIRST_NON_EMPTY','LAST_NON_EMPTY','REQUIRE_SAME','MERGE_DISTINCT','SUM'));

ALTER TABLE source_schemas
    ADD COLUMN record_path VARCHAR(300),
    ADD COLUMN row_type_field_id UUID REFERENCES source_fields(id),
    ADD COLUMN item_row_values VARCHAR(500),
    ADD COLUMN total_row_values VARCHAR(500),
    ADD COLUMN ignored_row_values VARCHAR(500);

--changeset collectra:014-delivery-and-item-fields
INSERT INTO field_definitions(id, tenant_id, field_key, label, data_type, category,
        collection, required, description, example_value, validation_rules, status,
        created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000010', NULL, 'delivery.channels', 'Delivery channels',
     'STRING', 'DELIVERY', TRUE, FALSE, 'EMAIL, SMS, WhatsApp and other delivery channels',
     'EMAIL', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000011', NULL, 'recipient.phones', 'Recipient phones',
     'STRING', 'RECIPIENT', TRUE, FALSE, 'Normalized recipient phone numbers',
     '+77011234567', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000012', NULL, 'recipient.emails', 'Recipient emails',
     'STRING', 'RECIPIENT', TRUE, FALSE, 'Normalized recipient email addresses',
     'client@example.kz', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000013', NULL, 'items.name', 'Item name',
     'STRING', 'ITEM', FALSE, FALSE, 'Invoice item name', 'Paper', '{}',
     'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000014', NULL, 'items.quantity', 'Item quantity',
     'DECIMAL', 'ITEM', FALSE, FALSE, 'Invoice item quantity', '2', '{}',
     'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000015', NULL, 'items.unitPrice', 'Item unit price',
     'DECIMAL', 'ITEM', FALSE, FALSE, 'Invoice item unit price', '500', '{}',
     'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000016', NULL, 'items.amount', 'Item amount',
     'DECIMAL', 'ITEM', FALSE, FALSE, 'Invoice item total amount', '1000', '{}',
     'ACTIVE', now(), now(), 0);

--changeset collectra:014-import-batches
CREATE TABLE import_batches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    mapping_profile_version_id UUID NOT NULL REFERENCES mapping_profiles(id),
    template_version_id UUID NOT NULL REFERENCES template_versions(id),
    status VARCHAR(30) NOT NULL,
    document_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_import_batch_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_import_batch_status CHECK (status IN ('PROCESSING','ACCEPTED','FAILED'))
);

CREATE TABLE import_batch_documents (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    document_order INTEGER NOT NULL,
    document_key VARCHAR(300) NOT NULL,
    generation_job_id UUID NOT NULL REFERENCES generation_jobs(id),
    CONSTRAINT uk_import_batch_document_order UNIQUE (import_batch_id, document_order),
    CONSTRAINT uk_import_batch_document_key UNIQUE (import_batch_id, document_key)
);
CREATE INDEX idx_import_batches_tenant_created ON import_batches(tenant_id, created_at DESC);
