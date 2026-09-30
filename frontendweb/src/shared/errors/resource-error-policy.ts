import { ApiError } from '../api/http-client';

export type ResourceErrorDisposition = 'forbidden' | 'not-found' | 'version-conflict' | 'other';

export function classifyResourceError(error: unknown): ResourceErrorDisposition {
  if (!(error instanceof ApiError)) return 'other';
  if (error.status === 403) return 'forbidden';
  if (error.status === 404) return 'not-found';
  if (error.problem?.code === 'VERSION_CONFLICT') return 'version-conflict';
  return 'other';
}
