--liquibase formatted sql

--changeset collectra:017-file-service-security
INSERT INTO permissions(id, code, module, description)
VALUES
    ('10000000-0000-0000-0000-000000000014', 'FILE_UPLOAD', 'file', 'Upload files'),
    ('10000000-0000-0000-0000-000000000015', 'FILE_READ', 'file', 'Read file metadata and content'),
    ('10000000-0000-0000-0000-000000000016', 'FILE_DELETE', 'file', 'Delete files'),
    ('10000000-0000-0000-0000-000000000017', 'FILE_ADMIN', 'file', 'Administer file lifecycle operations')
ON CONFLICT (code) DO NOTHING;

-- Tenant administrators receive the complete FileService permission set.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002'::uuid, p.id
FROM permissions p
WHERE p.code IN ('FILE_UPLOAD', 'FILE_READ', 'FILE_DELETE', 'FILE_ADMIN')
ON CONFLICT DO NOTHING;

-- Regular tenant users may read files but cannot upload/delete by default.
INSERT INTO role_permissions(role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000003'::uuid, p.id
FROM permissions p
WHERE p.code = 'FILE_READ'
ON CONFLICT DO NOTHING;
