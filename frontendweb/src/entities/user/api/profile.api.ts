import { apiRequest } from '../../../shared/api/http-client';
import type { MeDto, SessionDto } from '../model/user.types';

export function getProfile(): Promise<MeDto> {
  return apiRequest('/api/v1/identity/me');
}

export function updateProfile(command: { displayName: string; locale: string | null; timezone: string | null }): Promise<MeDto> {
  return apiRequest('/api/v1/identity/me', { method: 'PATCH', body: JSON.stringify(command) });
}

export function changePassword(command: { currentPassword: string; newPassword: string }): Promise<void> {
  return apiRequest('/api/v1/identity/me/change-password', { method: 'POST', body: JSON.stringify(command) });
}

export function getOwnSessions(): Promise<SessionDto[]> {
  return apiRequest('/api/v1/identity/me/sessions');
}

export function revokeOwnSession(id: string): Promise<void> {
  return apiRequest(`/api/v1/identity/me/sessions/${id}`, { method: 'DELETE' });
}
