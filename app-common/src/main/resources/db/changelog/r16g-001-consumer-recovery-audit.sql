CREATE TABLE enforcement_consumer_rebindings (
 id UUID PRIMARY KEY,
 operation_id UUID NOT NULL REFERENCES enforcement_operations(id) ON DELETE RESTRICT,
 consumer_id VARCHAR(100) NOT NULL,
 old_oauth_client_id VARCHAR(160) NOT NULL,
 old_binding_epoch BIGINT NOT NULL,
 new_oauth_client_id VARCHAR(160) NOT NULL,
 new_binding_epoch BIGINT NOT NULL,
 actor VARCHAR(255) NOT NULL,
 reason VARCHAR(1000) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT ck_rebinding_epoch_forward CHECK (new_binding_epoch > old_binding_epoch)
);
CREATE INDEX idx_enforcement_consumer_rebindings_operation
 ON enforcement_consumer_rebindings(operation_id,created_at);
