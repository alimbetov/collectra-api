-- Bind each acknowledgement to the exact OAuth client registration generation.
ALTER TABLE revocation_consumers ADD COLUMN binding_epoch BIGINT NOT NULL DEFAULT 1
  CHECK (binding_epoch > 0);
ALTER TABLE enforcement_operation_consumers ADD COLUMN binding_epoch BIGINT NOT NULL DEFAULT 1
  CHECK (binding_epoch > 0);
ALTER TABLE revocation_acks ADD COLUMN binding_epoch BIGINT NOT NULL DEFAULT 0;
-- Existing ACKs have no verifiable binding provenance; epoch 0 never satisfies new operations.
-- Prevent stale acknowledgements from being accepted after any client rebind/reenablement.
CREATE OR REPLACE FUNCTION bump_revocation_consumer_epoch()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.oauth_client_id IS DISTINCT FROM NEW.oauth_client_id
     OR OLD.enabled IS DISTINCT FROM NEW.enabled THEN
    NEW.binding_epoch := OLD.binding_epoch + 1;
  ELSE
    NEW.binding_epoch := OLD.binding_epoch;
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER trg_revocation_consumer_binding_epoch
BEFORE UPDATE ON revocation_consumers FOR EACH ROW
EXECUTE FUNCTION bump_revocation_consumer_epoch();
