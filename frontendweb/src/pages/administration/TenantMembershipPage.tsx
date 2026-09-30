import { FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import {
  assignMembershipRoles,
  changeMembershipStatus,
  getMembershipRoleIds,
  getMembershipSessions,
  getTenantMemberships,
  getTenantRoles,
  revokeAllMembershipSessions,
  revokeMembershipSession,
} from '../../entities/identity/api/tenant-admin.api';
import { PermissionGuard } from '../../features/auth/ui/PermissionGuard';

export function TenantMembershipPage() {
  const { membershipId = '' } = useParams();
  const queryClient = useQueryClient();
  const memberships = useQuery({ queryKey: ['tenant-admin', 'memberships'], queryFn: getTenantMemberships });
  const roles = useQuery({ queryKey: ['tenant-admin', 'roles'], queryFn: getTenantRoles });
  const assigned = useQuery({
    queryKey: ['tenant-admin', 'membership', membershipId, 'roles'],
    queryFn: () => getMembershipRoleIds(membershipId),
    enabled: Boolean(membershipId),
  });
  const sessions = useQuery({
    queryKey: ['tenant-admin', 'membership', membershipId, 'sessions'],
    queryFn: () => getMembershipSessions(membershipId),
    enabled: Boolean(membershipId),
  });
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);

  useEffect(() => {
    if (assigned.data) setSelectedRoles(assigned.data);
  }, [assigned.data]);

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['tenant-admin'] });
  const assign = useMutation({
    mutationFn: () => assignMembershipRoles(membershipId, selectedRoles),
    onSuccess: invalidate,
  });
  const status = useMutation({
    mutationFn: (active: boolean) => changeMembershipStatus(membershipId, active),
    onSuccess: invalidate,
  });
  const revoke = useMutation({
    mutationFn: (sessionId: string) => revokeMembershipSession(membershipId, sessionId),
    onSuccess: invalidate,
  });
  const revokeAll = useMutation({
    mutationFn: () => revokeAllMembershipSessions(membershipId),
    onSuccess: invalidate,
  });

  const membership = memberships.data?.find((item) => item.id === membershipId);
  if (memberships.isLoading) return <p>Loading membership…</p>;
  if (!membership) return <p role="alert">Membership not found.</p>;

  function submitRoles(event: FormEvent) {
    event.preventDefault();
    assign.mutate();
  }

  return (
    <main>
      <h1>{membership.displayName ?? membership.email ?? membership.userId}</h1>
      <p>{membership.email} · {membership.status}</p>

      <PermissionGuard permission="USER_BLOCK">
        <section>
          <h2>Membership status</h2>
          <button type="button" onClick={() => status.mutate(membership.status !== 'ACTIVE')}>
            {membership.status === 'ACTIVE' ? 'Block user' : 'Activate user'}
          </button>
        </section>
      </PermissionGuard>

      <PermissionGuard permission="ROLE_READ">
        <section>
          <h2>Roles</h2>
          <form onSubmit={submitRoles}>
            {(roles.data ?? []).map((role) => (
              <label key={role.id} style={{ display: 'block' }}>
                <input
                  type="checkbox"
                  checked={selectedRoles.includes(role.id)}
                  onChange={(event) => setSelectedRoles((current) =>
                    event.target.checked ? [...current, role.id] : current.filter((id) => id !== role.id))}
                  disabled={!assigned.data}
                />
                {role.code}
              </label>
            ))}
            <PermissionGuard permission="ROLE_ASSIGN">
              <button type="submit" disabled={assign.isPending}>Save roles</button>
            </PermissionGuard>
          </form>
        </section>
      </PermissionGuard>

      <section>
        <h2>Sessions</h2>
        <PermissionGuard permission="USER_UPDATE">
          <button type="button" onClick={() => revokeAll.mutate()} disabled={revokeAll.isPending}>
            Revoke all sessions
          </button>
        </PermissionGuard>
        <table>
          <thead><tr><th>Created</th><th>Last used</th><th>Expires</th><th>Source</th><th>Actions</th></tr></thead>
          <tbody>
            {(sessions.data ?? []).map((session) => (
              <tr key={session.id}>
                <td>{session.createdAt}</td><td>{session.lastUsedAt ?? '—'}</td>
                <td>{session.expiresAt}</td><td>{session.sourceIp ?? '—'} {session.userAgent ?? ''}</td>
                <td>
                  <PermissionGuard permission="USER_UPDATE">
                    <button type="button" onClick={() => revoke.mutate(session.id)} disabled={Boolean(session.revokedAt)}>
                      {session.revokedAt ? 'Revoked' : 'Revoke'}
                    </button>
                  </PermissionGuard>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </main>
  );
}
