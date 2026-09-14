import type { MeDto } from '../../../entities/user/model/user.types';
import { apiRequest } from '../../../shared/api/http-client';
import type { AuthTokens } from '../../../shared/auth/token-storage';

export interface LoginBySlugRequest {
  tenantSlug: string;
  email: string;
  password: string;
}

export function loginBySlug(request: LoginBySlugRequest): Promise<AuthTokens> {
  return apiRequest<AuthTokens>('/api/v1/auth/login/by-slug', {
    method: 'POST',
    body: request,
    auth: false,
    retryOnUnauthorized: false,
  });
}

export function logout(refreshToken: string): Promise<void> {
  return apiRequest<void>('/api/v1/auth/logout', {
    method: 'POST',
    body: { refreshToken },
    auth: false,
    retryOnUnauthorized: false,
  });
}

export function getCurrentUser(): Promise<MeDto> {
  return apiRequest<MeDto>('/api/v1/identity/me');
}
