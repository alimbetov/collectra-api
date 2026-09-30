import { FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { createTenantRole, deleteTenantRole, getTenantRoles, updateTenantRole } from '../../entities/identity/api/tenant-admin.api';
import { PermissionGuard } from '../../features/auth/ui/PermissionGuard';

export function TenantRolePage() {
  const { roleId } = useParams();
  const creating = !roleId;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const roles = useQuery({ queryKey: ['tenant-admin', 'roles'], queryFn: getTenantRoles });
  const role = roles.data?.find((item) => item.id === roleId);
  const [code, setCode] = useState('');
  const [permissions, setPermissions] = useState('');

  useEffect(() => {
    if (role) {
      setCode(role.code);
      setPermissions(role.permissions.join('\n'));
    }
  }, [role]);

  const save = useMutation({
    mutationFn: () => {
      const values = permissions.split(/[\n,]/).map((value) => value.trim()).filter(Boolean);
      return creating ? createTenantRole(code, values) : updateTenantRole(roleId!, code, values);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tenant-admin', 'roles'] });
      navigate('/administration');
    },
  });
  const remove = useMutation({
    mutationFn: () => deleteTenantRole(roleId!),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tenant-admin', 'roles'] });
      navigate('/administration');
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  if (!creating && roles.isLoading) return <p>Loading role…</p>;
  if (!creating && !role) return <p role="alert">Role not found.</p>;

  return (
    <main>
      <h1>{creating ? 'New role' : `Role ${role?.code}`}</h1>
      <form onSubmit={submit}>
        <label>Code<input value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} pattern="[A-Z][A-Z0-9_]{2,79}" required /></label>
        <label>Permissions<textarea value={permissions} onChange={(event) => setPermissions(event.target.value)} required rows={12} /></label>
        <button type="submit" disabled={save.isPending}>Save</button>
      </form>
      {!creating && !role?.system && (
        <PermissionGuard permission="ROLE_UPDATE">
          <button type="button" onClick={() => remove.mutate()} disabled={remove.isPending}>Delete role</button>
        </PermissionGuard>
      )}
    </main>
  );
}
