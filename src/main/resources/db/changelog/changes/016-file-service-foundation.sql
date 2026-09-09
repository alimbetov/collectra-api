--liquibase formatted sql

--changeset collectra:016-file-service-foundation
CREATE TABLE stored_file (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    project_id uuid,
    category varchar(40) NOT NULL,
    storage_provider varchar(30) NOT NULL,
    bucket varchar(128) NOT NULL,
    object_key varchar(1024) NOT NULL,
    original_filename varchar(512) NOT NULL,
    content_type varchar(255),
    size_bytes bigint,
    checksum_sha256 char(64),
    status varchar(40) NOT NULL,
    expires_at timestamptz,
    deleted_at timestamptz,
    created_by uuid,
    delete_attempts integer NOT NULL DEFAULT 0,
    last_delete_attempt_at timestamptz,
    last_error varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_stored_file_storage_location UNIQUE (bucket, object_key),
    CONSTRAINT ck_stored_file_size_non_negative CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT ck_stored_file_delete_attempts_non_negative CHECK (delete_attempts >= 0)
);

CREATE INDEX ix_stored_file_tenant_id_id
    ON stored_file (tenant_id, id);

CREATE INDEX ix_stored_file_status_expires_at
    ON stored_file (status, expires_at);

CREATE INDEX ix_stored_file_tenant_project_category_created
    ON stored_file (tenant_id, project_id, category, created_at DESC);

--rollback DROP TABLE stored_file;
