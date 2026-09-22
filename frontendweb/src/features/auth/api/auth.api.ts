import type { MeDto } from '../../../entities/user/model/user.types';
import { apiRequest } from '../../../shared/api/http-client';
import type { AuthTokens } from '../../../shared/auth/token-storage';

export interface LoginBySlugRequest {
  tenantSlug: string;
  email: string;
  password: string;
}

export interface PlatformLoginRequest {
  email: string;
  password: string;
}

export interface PlatformMeDto {
  userId: string;
}

export function loginBySlug(request: LoginBySlugRequest): Promise<AuthTokens> {
  return apiRequest<AuthTokens>('/api/v1/auth/login/by-slug', {
    method: 'POST',
    body: request,
    auth: false,
    retryOnUnauthorized: false,
  });
}

export function loginPlatform(request: PlatformLoginRequest): Promise<AuthTokens> {
  return apiRequest<AuthTokens>('/api/v1/platform/auth/login', {
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

export function logoutPlatform(refreshToken: string): Promise<void> {
  return apiRequest<void>('/api/v1/platform/auth/logout', {
    method: 'POST',
    body: { refreshToken },
    auth: false,
    retryOnUnauthorized: false,
  });
}

export function getCurrentUser(): Promise<MeDto> {
  return apiRequest<MeDto>('/api/v1/identity/me');
}

export function getCurrentPlatformUser(): Promise<PlatformMeDto> {
  return apiRequest<PlatformMeDto>('/api/v1/platform/me');
}
