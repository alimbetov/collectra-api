export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export type AuthSessionKind = 'tenant' | 'platform';

const REFRESH_TOKEN_KEY = 'collectra.refresh-token';
const SESSION_KIND_KEY = 'collectra.session-kind';
let accessToken: string | null = null;

function storage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.sessionStorage;
  } catch {
    return null;
  }
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getRefreshToken(): string | null {
  return storage()?.getItem(REFRESH_TOKEN_KEY) ?? null;
}

export function getSessionKind(): AuthSessionKind {
  return storage()?.getItem(SESSION_KIND_KEY) === 'platform' ? 'platform' : 'tenant';
}

export function setTokens(tokens: AuthTokens, kind: AuthSessionKind = 'tenant'): void {
  accessToken = tokens.accessToken;
  storage()?.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  storage()?.setItem(SESSION_KIND_KEY, kind);
}

export function clearTokens(): void {
  accessToken = null;
  storage()?.removeItem(REFRESH_TOKEN_KEY);
  storage()?.removeItem(SESSION_KIND_KEY);
}

export function hasRefreshToken(): boolean {
  return getRefreshToken() !== null;
}
