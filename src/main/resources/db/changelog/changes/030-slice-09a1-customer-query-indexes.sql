--liquibase formatted sql
--changeset collectra:030-slice-09a1-customer-query-indexes

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_customers_tenant_created_id
    ON customers(tenant_id, created_at DESC, id DESC);

CREATE INDEX idx_customers_tenant_updated_id
    ON customers(tenant_id, updated_at DESC, id DESC);

CREATE INDEX idx_customers_tenant_manager_created_id
    ON customers(tenant_id, manager_user_id, created_at DESC, id DESC);

CREATE INDEX idx_customers_display_name_trgm
    ON customers USING GIN (lower(display_name) gin_trgm_ops);

CREATE INDEX idx_customers_tenant_external_lower
    ON customers(tenant_id, (lower(external_id)) text_pattern_ops);

CREATE INDEX idx_customer_segments_tenant_active_name_id
    ON customer_segments(tenant_id, active, name, id);

CREATE INDEX idx_customer_segments_name_trgm
    ON customer_segments USING GIN (lower(name) gin_trgm_ops);

CREATE INDEX idx_customer_segments_tenant_code_lower
    ON customer_segments(tenant_id, (lower(code)) text_pattern_ops);
