-- Mandatory consumer topology is an explicit release gate, not inferred from enabled rows.
ALTER TABLE revocation_consumers ADD COLUMN required_for_enforcement BOOLEAN NOT NULL DEFAULT FALSE;
INSERT INTO revocation_consumers(consumer_id,enabled,required_for_enforcement)
VALUES ('collectra-api',false,true),('app-b-api',false,true)
ON CONFLICT (consumer_id) DO UPDATE SET required_for_enforcement=TRUE;
-- Consumer enablement requires separately verified OAuth client binding.
CREATE INDEX idx_revocation_required_consumers ON revocation_consumers(required_for_enforcement,enabled);
