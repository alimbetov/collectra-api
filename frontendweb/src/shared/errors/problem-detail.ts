import type { ProblemDetailDto } from '../api/contracts';
import { ApiError } from '../api/http-client';

const SAFE_SUPPORT_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;

export interface ProblemViewModel {
  status: number | null;
  code: string | null;
  detail: string | null;
  supportId: string | null;
}

export function safeSupportId(problem: ProblemDetailDto | null | undefined): string | null {
  const candidate = problem?.correlationId ?? problem?.traceId;
  return candidate && SAFE_SUPPORT_ID.test(candidate) ? candidate : null;
}

export function toProblemViewModel(error: unknown): ProblemViewModel {
  if (!(error instanceof ApiError)) {
    return { status: null, code: null, detail: null, supportId: null };
  }

  const problem = error.problem;
  const status = problem?.status ?? error.status;
  return {
    status,
    code: typeof problem?.code === 'string' ? problem.code : null,
    // Backend validation/conflict details are actionable. Never expose opaque 5xx details.
    detail: status >= 400 && status < 500 ? problem?.detail ?? problem?.title ?? null : null,
    supportId: safeSupportId(problem),
  };
}
