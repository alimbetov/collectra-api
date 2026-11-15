-- IAM-R3.0B: retain existing Collectra UUIDs and all historical foreign keys.
ALTER TABLE user_accounts ADD COLUMN credential_authority VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_credential_authority
    CHECK (credential_authority IN ('LEGACY','KEYCLOAK'));
