CREATE TABLE collectra_keycloak_onboarding (
    id UUID PRIMARY KEY,
    local_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    subject_id UUID NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VERIFIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at TIMESTAMPTZ,
    CONSTRAINT uk_keycloak_onboarding_local UNIQUE (local_user_id)
);
