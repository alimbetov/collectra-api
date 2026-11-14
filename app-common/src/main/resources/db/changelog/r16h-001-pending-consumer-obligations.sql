-- Offline mandatory consumers must not prevent a security block from committing.
-- NULL oauth_client_id is an unresolved obligation, never a successful ACK.
ALTER TABLE enforcement_operation_consumers ALTER COLUMN oauth_client_id DROP NOT NULL;
ALTER TABLE enforcement_operation_consumers DROP CONSTRAINT ck_enforcement_operation_consumer_client;
ALTER TABLE enforcement_operation_consumers ADD CONSTRAINT ck_enforcement_operation_consumer_client
  CHECK (oauth_client_id IS NULL OR length(trim(oauth_client_id)) > 0);
ALTER TABLE enforcement_consumer_rebindings ALTER COLUMN old_oauth_client_id DROP NOT NULL;
