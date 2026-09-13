import type { ProblemDetailDto } from './contracts';
import { emitSessionLost } from '../auth/auth-events';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens,
  type AuthTokens,
} from '../auth/token-storage';

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetailDto | null;

  constructor(status: number, problem: ProblemDetailDto | null) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  auth?: boolean;
  retryOnUnauthorized?: boolean;
}

const isBodyAllowed = (method?: string) => {
  const normalized = (method ?? 'GET').toUpperCase();
  return normalized !== 'GET' && normalized !== 'HEAD';
};

async function readProblem(response: Response): Promise<ProblemDetailDto | null> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    return null;
  }

  try {
    return (await response.json()) as ProblemDetailDto;
  } catch {
    return null;
  }
}

function prepareRequest(options: ApiRequestOptions): RequestInit {
  const { body: requestBody, auth = true, retryOnUnauthorized: _retry, ...requestInit } = options;
  const headers = new Headers(options.headers);
  const bodyAllowed = isBodyAllowed(options.method);
  let body: BodyInit | undefined;

  if (bodyAllowed && requestBody !== undefined) {
    if (requestBody instanceof FormData) {
      body = requestBody;
    } else {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(requestBody);
    }
  }

  headers.set('Accept', 'application/json');

  const accessToken = getAccessToken();
  if (auth && accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  return {
    ...requestInit,
    headers,
    body,
  };
}

async function execute(path: string, options: ApiRequestOptions): Promise<Response> {
  return fetch(path, prepareRequest(options));
}

function invalidateSession(): false {
  clearTokens();
  emitSessionLost();
  return false;
}

function isAuthTokens(value: unknown): value is AuthTokens {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<AuthTokens>;
  return (
    typeof candidate.accessToken === 'string' &&
    candidate.accessToken.length > 0 &&
    typeof candidate.refreshToken === 'string' &&
    candidate.refreshToken.length > 0 &&
    typeof candidate.tokenType === 'string' &&
    typeof candidate.expiresIn === 'number'
  );
}

let refreshPromise: Promise<boolean> | null = null;

async function performRefresh(refreshToken: string): Promise<boolean> {
  try {
    const response = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      return invalidateSession();
    }

    const tokens: unknown = await response.json();
    if (!isAuthTokens(tokens)) {
      return invalidateSession();
    }

    setTokens(tokens);
    return true;
  } catch {
    return invalidateSession();
  }
}

export function refreshAccessToken(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return Promise.resolve(false);
  }

  if (!refreshPromise) {
    refreshPromise = performRefresh(refreshToken).finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const auth = options.auth ?? true;
  const retryOnUnauthorized = options.retryOnUnauthorized ?? true;
  let response = await execute(path, options);

  if (response.status === 401 && auth && retryOnUnauthorized && getRefreshToken()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await execute(path, { ...options, retryOnUnauthorized: false });
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
