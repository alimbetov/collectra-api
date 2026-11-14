-- Collectra UUIDs, memberships, tenants and legacy foreign keys remain unchanged.
CREATE TABLE collectra_identity_links (
    issuer VARCHAR(512) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    collectra_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('VERIFIED','REVOKED')),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (issuer,subject_id),
    CONSTRAINT uk_collectra_identity_link_user UNIQUE (collectra_user_id),
    CONSTRAINT ck_collectra_identity_link_verified CHECK
        (status <> 'VERIFIED' OR verified_at IS NOT NULL)
);
CREATE INDEX idx_collectra_identity_links_local_user ON collectra_identity_links(collectra_user_id);
