/**
 * Opt-in same-origin IAM BFF transport. No OAuth tokens are read or persisted.
 * Not wired into the legacy AuthProvider until Keycloak browser E2E is green.
 */
export async function bffRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/api/v1/') || path.includes('..') || path.includes('%')
      || path.includes('\\') || path.startsWith('//')) {
    throw new Error('Invalid Collectra API path');
  }
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (method !== 'GET' && method !== 'HEAD') {
    const csrf = await fetch('/api/v1/iam/csrf', { credentials: 'same-origin', cache: 'no-store' });
    if (!csrf.ok) throw new Error('CSRF bootstrap failed');
    const value = await csrf.json() as { headerName: string; token: string };
    if (value.headerName !== 'X-XSRF-TOKEN' || !value.token)
      throw new Error('Invalid CSRF contract');
    headers.set(value.headerName, value.token);
  }
  headers.delete('Authorization');
  const response = await fetch('/api/v1/iam/collectra' + path, {
    ...options, headers, credentials: 'same-origin', redirect: 'error', cache: 'no-store',
  });
  if (!response.ok) throw new Error('BFF HTTP ' + response.status);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
