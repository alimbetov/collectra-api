--liquibase formatted sql

--changeset collectra:011-field-catalog-permissions
INSERT INTO permissions(id, code, module, description)
VALUES
    ('10000000-0000-0000-0000-000000000014', 'FIELD_READ', 'template', 'Read template field catalog'),
    ('10000000-0000-0000-0000-000000000015', 'FIELD_CREATE', 'template', 'Create custom template fields'),
    ('10000000-0000-0000-0000-000000000016', 'FIELD_UPDATE', 'template', 'Update or archive custom template fields');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM permissions WHERE code IN ('FIELD_READ', 'FIELD_CREATE', 'FIELD_UPDATE');

INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003', id
FROM permissions WHERE code = 'FIELD_READ';

--changeset collectra:011-system-field-catalog
INSERT INTO field_definitions(id, tenant_id, field_key, label, data_type, category,
        collection, required, description, example_value, validation_rules, status,
        created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000001', NULL, 'document.number', 'Document number',
     'STRING', 'DOCUMENT', FALSE, FALSE, 'Canonical document number', 'INV-2026-001', '{}',
     'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000002', NULL, 'document.date', 'Document date',
     'DATE', 'DOCUMENT', FALSE, FALSE, 'Canonical document date', '2026-09-08', '{}',
     'ACTIVE', now(), now(), 0),
    ('20000000-0000-0000-0000-000000000003', NULL, 'customer.name', 'Customer name',
     'STRING', 'CUSTOMER', FALSE, FALSE, 'Customer display name', 'Example Company', '{}',
     'ACTIVE', now(), now(), 0);
