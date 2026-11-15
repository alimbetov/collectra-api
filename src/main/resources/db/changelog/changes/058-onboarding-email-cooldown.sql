ALTER TABLE collectra_keycloak_onboarding
    ADD COLUMN verification_requested_at TIMESTAMPTZ;

CREATE INDEX idx_keycloak_onboarding_pending_delivery
    ON collectra_keycloak_onboarding (verification_requested_at)
    WHERE status = 'PENDING';
