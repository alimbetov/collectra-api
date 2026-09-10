--liquibase formatted sql

--changeset collectra:019-template-channel-variants
ALTER TABLE template_versions
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'PDF',
    ADD COLUMN subject VARCHAR(300),
    ADD COLUMN builder_json JSONB;

ALTER TABLE template_versions DROP CONSTRAINT uk_template_version;
ALTER TABLE template_versions
    ADD CONSTRAINT uk_template_version UNIQUE (template_id, template_version, locale, channel);
ALTER TABLE template_versions
    ADD CONSTRAINT ck_template_channel CHECK (channel IN ('EMAIL','SMS','WHATSAPP','TELEGRAM','PDF'));

--changeset collectra:019-template-assets
CREATE TABLE template_assets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    asset_key VARCHAR(80) NOT NULL,
    file_id UUID NOT NULL REFERENCES stored_file(id),
    alt_text VARCHAR(300),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_template_asset_key UNIQUE (tenant_id, asset_key),
    CONSTRAINT ck_template_asset_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);
CREATE INDEX idx_template_assets_tenant_status ON template_assets(tenant_id, status, asset_key);

--changeset collectra:019-recipient-canonical-fields
INSERT INTO field_definitions(id, tenant_id, field_key, label, data_type, category,
        collection, required, description, example_value, validation_rules, status,
        created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000101', NULL, 'recipient.name', 'Recipient name',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'Recipient display name', 'Aigerim S.', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000102', NULL, 'recipient.email', 'Recipient email',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'Email destination', 'finance@example.kz', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000103', NULL, 'recipient.phone', 'Recipient phone',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'E.164 phone destination', '+77010000001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000104', NULL, 'recipient.whatsapp', 'Recipient WhatsApp',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'WhatsApp destination', '+77010000001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000105', NULL, 'recipient.telegram', 'Recipient Telegram',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'Telegram username or chat identifier', '@example_fin', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000106', NULL, 'recipient.locale', 'Recipient locale',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'Preferred message locale', 'ru', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000107', NULL, 'recipient.timezone', 'Recipient timezone',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'IANA timezone', 'Asia/Almaty', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000108', NULL, 'recipient.preferred_channel', 'Preferred channel',
     'STRING', 'RECIPIENT', FALSE, FALSE, 'Preferred delivery channel', 'WHATSAPP', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000109', NULL, 'recipient.consent_email', 'Email consent',
     'BOOLEAN', 'RECIPIENT', FALSE, FALSE, 'Email delivery consent', 'true', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000110', NULL, 'recipient.consent_sms', 'SMS consent',
     'BOOLEAN', 'RECIPIENT', FALSE, FALSE, 'SMS delivery consent', 'true', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000111', NULL, 'recipient.consent_whatsapp', 'WhatsApp consent',
     'BOOLEAN', 'RECIPIENT', FALSE, FALSE, 'WhatsApp delivery consent', 'true', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000112', NULL, 'recipient.consent_telegram', 'Telegram consent',
     'BOOLEAN', 'RECIPIENT', FALSE, FALSE, 'Telegram delivery consent', 'true', '{}', 'ACTIVE', now(), now(), 0);

--changeset collectra:019-items-canonical-fields
INSERT INTO field_definitions(id, tenant_id, field_key, label, data_type, category,
        collection, required, description, example_value, validation_rules, status,
        created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000121', NULL, 'items.line_no', 'Item line number',
     'INTEGER', 'ITEMS', TRUE, FALSE, 'Ordered document detail line number', '1', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000122', NULL, 'items.code', 'Item code',
     'STRING', 'ITEMS', TRUE, FALSE, 'Product or service code', 'SKU-001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000123', NULL, 'items.name', 'Item name',
     'STRING', 'ITEMS', TRUE, FALSE, 'Product or service name', 'Service fee', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000124', NULL, 'items.description', 'Item description',
     'STRING', 'ITEMS', TRUE, FALSE, 'Optional detail description', 'Monthly service', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000125', NULL, 'items.qty', 'Item quantity',
     'DECIMAL', 'ITEMS', TRUE, FALSE, 'Detail quantity', '2', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000126', NULL, 'items.unit', 'Item unit',
     'STRING', 'ITEMS', TRUE, FALSE, 'Quantity unit', 'pcs', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000127', NULL, 'items.price', 'Item price',
     'DECIMAL', 'ITEMS', TRUE, FALSE, 'Unit price', '5000', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000128', NULL, 'items.amount', 'Item amount',
     'DECIMAL', 'ITEMS', TRUE, FALSE, 'Detail line amount', '10000', '{}', 'ACTIVE', now(), now(), 0);
