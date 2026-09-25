--liquibase formatted sql

--changeset collectra:050-file-registry-indexes
CREATE INDEX ix_stored_file_tenant_created_id
    ON stored_file (tenant_id, created_at DESC, id);

CREATE INDEX ix_stored_file_tenant_status_created_id
    ON stored_file (tenant_id, status, created_at DESC, id);

CREATE INDEX ix_stored_file_tenant_category_created_id
    ON stored_file (tenant_id, category, created_at DESC, id);

--rollback DROP INDEX IF EXISTS ix_stored_file_tenant_category_created_id;
--rollback DROP INDEX IF EXISTS ix_stored_file_tenant_status_created_id;
--rollback DROP INDEX IF EXISTS ix_stored_file_tenant_created_id;
