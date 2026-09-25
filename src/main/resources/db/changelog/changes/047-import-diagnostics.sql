--liquibase formatted sql

--changeset collectra:047-import-record-diagnostics
CREATE TABLE import_record_diagnostic (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    import_id UUID NOT NULL REFERENCES import_batches(id) ON DELETE CASCADE,
    record_number INTEGER NOT NULL,
    record_order INTEGER NOT NULL,
    document_key VARCHAR(300),
    stage VARCHAR(60) NOT NULL,
    field_path VARCHAR(300),
    error_code VARCHAR(100) NOT NULL,
    safe_detail VARCHAR(500) NOT NULL,
    masked_source_value VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_import_record_diagnostic_record_number CHECK (record_number >= 0),
    CONSTRAINT ck_import_record_diagnostic_record_order CHECK (record_order >= 0)
);

CREATE INDEX idx_import_diag_tenant_import_record_field_id
    ON import_record_diagnostic(tenant_id, import_id, record_number, field_path, id);
