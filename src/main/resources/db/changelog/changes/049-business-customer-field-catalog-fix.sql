--liquibase formatted sql

--changeset collectra:049-business-customer-field-catalog-fix
INSERT INTO field_definitions(
    id, tenant_id, field_key, label, data_type, category, collection, required,
    description, example_value, validation_rules, status, created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000040', NULL, 'customer.externalId', 'Customer external ID', 'STRING', 'CUSTOMER', FALSE, TRUE, 'External business key of the customer', 'CUST-001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000041', NULL, 'customer.type', 'Customer type', 'STRING', 'CUSTOMER', FALSE, FALSE, 'INDIVIDUAL or COMPANY', 'COMPANY', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000042', NULL, 'customer.displayName', 'Customer display name', 'STRING', 'CUSTOMER', FALSE, FALSE, 'Display name used in communications', 'Example Company', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000043', NULL, 'customer.firstName', 'Customer first name', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Ruslan', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000044', NULL, 'customer.lastName', 'Customer last name', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Alimbetov', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000045', NULL, 'customer.companyName', 'Company name', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Example LLP', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000046', NULL, 'customer.locale', 'Customer locale', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'ru-KZ', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000047', NULL, 'customer.timezone', 'Customer timezone', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Asia/Almaty', '{}', 'ACTIVE', now(), now(), 0)
ON CONFLICT DO NOTHING;
