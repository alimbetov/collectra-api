import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getTenantMemberships, getTenantRoles } from '../../entities/identity/api/tenant-admin.api';
import { PermissionGuard } from '../../features/auth/ui/PermissionGuard';

export function TenantAdministrationPage() {
  const users = useQuery({ queryKey: ['tenant-admin', 'memberships'], queryFn: getTenantMemberships });
  const roles = useQuery({ queryKey: ['tenant-admin', 'roles'], queryFn: getTenantRoles });

  if (users.isLoading || roles.isLoading) return <p>Loading administration…</p>;
  if (users.isError || roles.isError) return <p role="alert">Failed to load tenant administration.</p>;

  return (
    <main>
      <header>
        <h1>Administration</h1>
        <p>Tenant users, memberships, roles and active sessions.</p>
      </header>

      <section aria-labelledby="users-heading">
        <h2 id="users-heading">Users</h2>
        <table>
          <thead><tr><th>User</th><th>Email</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>
            {(users.data ?? []).map((membership) => (
              <tr key={membership.id}>
                <td>{membership.displayName ?? membership.userId}</td>
                <td>{membership.email ?? '—'}</td>
                <td>{membership.status}</td>
                <td><Link to={`/administration/users/${membership.id}`}>Manage</Link></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <PermissionGuard permission="ROLE_READ">
        <section aria-labelledby="roles-heading">
          <h2 id="roles-heading">Roles</h2>
          <PermissionGuard permission="ROLE_CREATE">
            <Link to="/administration/roles/new">New role</Link>
          </PermissionGuard>
          <table>
            <thead><tr><th>Code</th><th>Scope</th><th>Type</th><th>Permissions</th></tr></thead>
            <tbody>
              {(roles.data ?? []).map((role) => (
                <tr key={role.id}>
                  <td>
                    {role.system ? role.code : <Link to={`/administration/roles/${role.id}`}>{role.code}</Link>}
                  </td>
                  <td>{role.scope}</td>
                  <td>{role.system ? 'System' : 'Custom'}</td>
                  <td>{role.permissions.join(', ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </PermissionGuard>
    </main>
  );
}
