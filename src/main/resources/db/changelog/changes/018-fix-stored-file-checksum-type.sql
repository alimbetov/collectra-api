--liquibase formatted sql

--changeset collectra:018-fix-stored-file-checksum-type
ALTER TABLE stored_file
    ALTER COLUMN checksum_sha256 TYPE varchar(64)
    USING checksum_sha256::varchar(64);

--rollback ALTER TABLE stored_file
--rollback     ALTER COLUMN checksum_sha256 TYPE char(64)
--rollback     USING checksum_sha256::char(64);
