--liquibase formatted sql

--changeset collectra:024-business-import-field-catalog
INSERT INTO field_definitions(
    id, tenant_id, field_key, label, data_type, category, collection, required,
    description, example_value, validation_rules, status, created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000010', NULL, 'customer.externalId', 'Customer external ID', 'STRING', 'CUSTOMER', FALSE, TRUE, 'External business key of the customer', 'CUST-001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000011', NULL, 'customer.type', 'Customer type', 'STRING', 'CUSTOMER', FALSE, FALSE, 'INDIVIDUAL or COMPANY', 'COMPANY', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000012', NULL, 'customer.displayName', 'Customer display name', 'STRING', 'CUSTOMER', FALSE, FALSE, 'Display name used in communications', 'Example Company', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000013', NULL, 'customer.firstName', 'Customer first name', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Ruslan', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000014', NULL, 'customer.lastName', 'Customer last name', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Alimbetov', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000015', NULL, 'customer.companyName', 'Company name', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Example LLP', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000016', NULL, 'customer.locale', 'Customer locale', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'ru-KZ', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000017', NULL, 'customer.timezone', 'Customer timezone', 'STRING', 'CUSTOMER', FALSE, FALSE, NULL, 'Asia/Almaty', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000020', NULL, 'invoice.externalId', 'Invoice external ID', 'STRING', 'RECEIVABLE', FALSE, TRUE, 'External business key of the invoice', 'INV-001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000021', NULL, 'invoice.invoiceNumber', 'Invoice number', 'STRING', 'RECEIVABLE', FALSE, FALSE, NULL, 'INV-2026-001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000022', NULL, 'invoice.invoiceDate', 'Invoice date', 'DATE', 'RECEIVABLE', FALSE, FALSE, NULL, '2026-09-01', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000023', NULL, 'invoice.dueDate', 'Invoice due date', 'DATE', 'RECEIVABLE', FALSE, TRUE, NULL, '2026-09-15', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000024', NULL, 'invoice.amount', 'Invoice amount', 'DECIMAL', 'RECEIVABLE', FALSE, TRUE, NULL, '150000.00', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000025', NULL, 'invoice.currency', 'Invoice currency', 'STRING', 'RECEIVABLE', FALSE, TRUE, NULL, 'KZT', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000026', NULL, 'invoice.contractId', 'Contract ID', 'STRING', 'RECEIVABLE', FALSE, FALSE, NULL, NULL, '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000027', NULL, 'invoice.documentFileId', 'Invoice document file ID', 'STRING', 'RECEIVABLE', FALSE, FALSE, NULL, NULL, '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000030', NULL, 'payment.externalId', 'Payment external ID', 'STRING', 'PAYMENT', FALSE, TRUE, 'External business key of the payment', 'PAY-001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000031', NULL, 'payment.paymentDate', 'Payment date', 'DATE', 'PAYMENT', FALSE, TRUE, NULL, '2026-09-11', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000032', NULL, 'payment.amount', 'Payment amount', 'DECIMAL', 'PAYMENT', FALSE, TRUE, NULL, '50000.00', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000033', NULL, 'payment.currency', 'Payment currency', 'STRING', 'PAYMENT', FALSE, TRUE, NULL, 'KZT', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000034', NULL, 'payment.reference', 'Payment reference', 'STRING', 'PAYMENT', FALSE, FALSE, NULL, 'ERP-00001', '{}', 'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000035', NULL, 'payment.source', 'Payment source', 'STRING', 'PAYMENT', FALSE, FALSE, NULL, '1C', '{}', 'ACTIVE', now(), now(), 0)
ON CONFLICT DO NOTHING;
