--liquibase formatted sql

--changeset collectra:037-identity-user-option-indexes
CREATE INDEX idx_membership_tenant_status_user
    ON tenant_memberships(tenant_id, status, user_id);

CREATE INDEX idx_user_account_email_prefix
    ON user_accounts(lower(email) text_pattern_ops);

CREATE INDEX idx_user_account_display_name_prefix
    ON user_accounts(lower(display_name) text_pattern_ops)
    WHERE display_name IS NOT NULL;
