import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http-client';
import { safeSupportId, toProblemViewModel } from './problem-detail';

describe('safe ProblemDetail mapping', () => {
  it('prefers a validated correlation id', () => {
    expect(safeSupportId({ status: 409, correlationId: 'corr-123', traceId: 'trace-456' })).toBe('corr-123');
  });

  it('rejects identifiers that can inject UI content or exceed the bound', () => {
    expect(safeSupportId({ status: 500, traceId: '<script>' })).toBeNull();
    expect(safeSupportId({ status: 500, traceId: 'a'.repeat(129) })).toBeNull();
  });

  it('keeps safe 4xx details for actionable feedback', () => {
    const error = new ApiError(409, { status: 409, code: 'VERSION_CONFLICT', detail: 'Запись уже изменена' });
    expect(toProblemViewModel(error)).toMatchObject({ status: 409, code: 'VERSION_CONFLICT', detail: 'Запись уже изменена' });
  });

  it('suppresses opaque server details', () => {
    const error = new ApiError(500, { status: 500, detail: 'password=secret', traceId: 'safe-trace' });
    expect(toProblemViewModel(error)).toMatchObject({ status: 500, detail: null, supportId: 'safe-trace' });
  });
});
