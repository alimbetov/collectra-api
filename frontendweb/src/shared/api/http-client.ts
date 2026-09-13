import type { ProblemDetailDto } from './contracts';

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

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  const bodyAllowed = isBodyAllowed(options.method);
  let body: BodyInit | undefined;

  if (bodyAllowed && options.body !== undefined) {
    if (options.body instanceof FormData) {
      body = options.body;
    } else {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(options.body);
    }
  }

  headers.set('Accept', 'application/json');

  const response = await fetch(path, {
    ...options,
    headers,
    body,
  });

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
