import { apiRequest } from '../../../shared/api/http-client';

export interface TenantMembershipDto {
  id: string;
  userId: string;
  email: string | null;
  displayName: string | null;
  status: string;
}

export interface TenantRoleDto {
  id: string;
  code: string;
  scope: string;
  system: boolean;
  permissions: string[];
}

export interface TenantSessionDto {
  id: string;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
  userAgent: string | null;
  sourceIp: string | null;
}

export function getTenantMemberships(): Promise<TenantMembershipDto[]> {
  return apiRequest('/api/v1/identity/users');
}

export function getTenantRoles(): Promise<TenantRoleDto[]> {
  return apiRequest('/api/v1/identity/roles');
}

export function getMembershipRoleIds(membershipId: string): Promise<string[]> {
  return apiRequest(`/api/v1/identity/memberships/${membershipId}/roles`);
}

export function assignMembershipRoles(membershipId: string, roleIds: string[]): Promise<void> {
  return apiRequest(`/api/v1/identity/memberships/${membershipId}/roles`, {
    method: 'PUT',
    body: JSON.stringify({ roleIds }),
  });
}

export function changeMembershipStatus(membershipId: string, active: boolean): Promise<void> {
  return apiRequest(`/api/v1/identity/memberships/${membershipId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ active }),
  });
}

export function getMembershipSessions(membershipId: string): Promise<TenantSessionDto[]> {
  return apiRequest(`/api/v1/identity/memberships/${membershipId}/sessions`);
}

export function revokeMembershipSession(membershipId: string, sessionId: string): Promise<void> {
  return apiRequest(`/api/v1/identity/memberships/${membershipId}/sessions/${sessionId}`, {
    method: 'DELETE',
  });
}

export function revokeAllMembershipSessions(membershipId: string): Promise<void> {
  return apiRequest(`/api/v1/identity/memberships/${membershipId}/sessions`, {
    method: 'DELETE',
  });
}

export function createTenantRole(code: string, permissions: string[]): Promise<TenantRoleDto> {
  return apiRequest('/api/v1/identity/roles', {
    method: 'POST',
    body: JSON.stringify({ code, permissions }),
  });
}

export function updateTenantRole(id: string, code: string, permissions: string[]): Promise<TenantRoleDto> {
  return apiRequest(`/api/v1/identity/roles/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ code, permissions }),
  });
}

export function deleteTenantRole(id: string): Promise<void> {
  return apiRequest(`/api/v1/identity/roles/${id}`, { method: 'DELETE' });
}
