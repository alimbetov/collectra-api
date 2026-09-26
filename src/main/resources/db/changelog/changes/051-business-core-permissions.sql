--liquibase formatted sql

--changeset collectra:051-business-core-permissions
INSERT INTO permissions(id, code, module, description) VALUES
    ('10000000-0000-0000-0000-000000000051', 'CUSTOMER_READ', 'customer', 'Read customers and customer-owned contact/segment data'),
    ('10000000-0000-0000-0000-000000000052', 'CUSTOMER_MANAGE', 'customer', 'Manage customers and customer-owned contact/segment data'),
    ('10000000-0000-0000-0000-000000000053', 'CONTRACT_READ', 'contract', 'Read contracts'),
    ('10000000-0000-0000-0000-000000000054', 'CONTRACT_MANAGE', 'contract', 'Manage contract lifecycle'),
    ('10000000-0000-0000-0000-000000000055', 'RECEIVABLE_READ', 'receivable', 'Read invoices, payments and allocations'),
    ('10000000-0000-0000-0000-000000000056', 'RECEIVABLE_MANAGE', 'receivable', 'Manage invoices, payments, allocations and reversals'),
    ('10000000-0000-0000-0000-000000000057', 'COLLECTION_READ', 'collection', 'Read collection cases and histories'),
    ('10000000-0000-0000-0000-000000000058', 'COLLECTION_MANAGE', 'collection', 'Manage collection cases, promises, disputes and actions');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'TENANT_ADMIN'
  AND role.tenant_id IS NULL
  AND permission.code IN (
      'CUSTOMER_READ', 'CUSTOMER_MANAGE',
      'CONTRACT_READ', 'CONTRACT_MANAGE',
      'RECEIVABLE_READ', 'RECEIVABLE_MANAGE',
      'COLLECTION_READ', 'COLLECTION_MANAGE'
  );
