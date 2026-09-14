export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

const REFRESH_TOKEN_KEY = 'collectra.refresh-token';
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

export function setTokens(tokens: AuthTokens): void {
  accessToken = tokens.accessToken;
  storage()?.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
}

export function clearTokens(): void {
  accessToken = null;
  storage()?.removeItem(REFRESH_TOKEN_KEY);
}

export function hasRefreshToken(): boolean {
  return getRefreshToken() !== null;
}
