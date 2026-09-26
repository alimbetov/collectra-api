declare const __API_BASE_URL__: string | undefined;

const absoluteUrlPattern = /^[a-z][a-z\d+\-.]*:\/\//i;

function normalizeBaseUrl(value: string | undefined): string {
  return (value ?? '').trim().replace(/\/+$/, '');
}

export const apiBaseUrl = normalizeBaseUrl(
  typeof __API_BASE_URL__ === 'undefined' ? undefined : __API_BASE_URL__,
);

export function buildApiUrl(path: string): string {
  if (!apiBaseUrl || absoluteUrlPattern.test(path)) {
    return path;
  }
  return `${apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`;
}
