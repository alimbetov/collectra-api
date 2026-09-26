--liquibase formatted sql

--changeset collectra:051-business-core-permissions
INSERT INTO permissions(id, code, module, description) VALUES
    ('51a00000-0000-4000-8000-000000000001', 'CUSTOMER_READ', 'customer', 'Read customers and customer-owned contact/segment data'),
    ('51a00000-0000-4000-8000-000000000002', 'CUSTOMER_MANAGE', 'customer', 'Manage customers and customer-owned contact/segment data'),
    ('51a00000-0000-4000-8000-000000000003', 'CONTRACT_READ', 'contract', 'Read contracts'),
    ('51a00000-0000-4000-8000-000000000004', 'CONTRACT_MANAGE', 'contract', 'Manage contract lifecycle'),
    ('51a00000-0000-4000-8000-000000000005', 'RECEIVABLE_READ', 'receivable', 'Read invoices, payments and allocations'),
    ('51a00000-0000-4000-8000-000000000006', 'RECEIVABLE_MANAGE', 'receivable', 'Manage invoices, payments, allocations and reversals'),
    ('51a00000-0000-4000-8000-000000000007', 'COLLECTION_READ', 'collection', 'Read collection cases and histories'),
    ('51a00000-0000-4000-8000-000000000008', 'COLLECTION_MANAGE', 'collection', 'Manage collection cases, promises, disputes and actions');

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
