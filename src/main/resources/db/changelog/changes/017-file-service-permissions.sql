--liquibase formatted sql

--changeset collectra:017-file-service-permissions
INSERT INTO permissions(id, code, module, description) VALUES
    ('10000000-0000-0000-0000-000000000040', 'FILE_READ', 'file', 'Read file metadata and download file content'),
    ('10000000-0000-0000-0000-000000000041', 'FILE_UPLOAD', 'file', 'Upload files into Collectra file storage'),
    ('10000000-0000-0000-0000-000000000042', 'FILE_DELETE', 'file', 'Delete files from Collectra file storage'),
    ('10000000-0000-0000-0000-000000000043', 'FILE_ADMIN', 'file', 'Administer file lifecycle and cleanup');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'TENANT_ADMIN'
  AND permission.code IN ('FILE_READ', 'FILE_UPLOAD', 'FILE_DELETE', 'FILE_ADMIN');

INSERT INTO role_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'TENANT_USER'
  AND permission.code IN ('FILE_READ', 'FILE_UPLOAD');
