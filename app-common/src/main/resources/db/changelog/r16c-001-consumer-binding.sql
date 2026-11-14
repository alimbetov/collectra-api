ALTER TABLE revocation_consumers ADD COLUMN oauth_client_id VARCHAR(160);
CREATE UNIQUE INDEX uk_revocation_consumers_client ON revocation_consumers(oauth_client_id) WHERE oauth_client_id IS NOT NULL;
CREATE INDEX idx_revocation_acks_subject ON revocation_acks(issuer,subject_id,version);
