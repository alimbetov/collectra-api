import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http-client';
import { classifyResourceError } from './resource-error-policy';

describe('resource error policy', () => {
  it.each([
    [403, null, 'forbidden'],
    [404, null, 'not-found'],
    [409, { code: 'VERSION_CONFLICT' }, 'version-conflict'],
    [500, null, 'other'],
  ] as const)('classifies HTTP %s as %s', (status, problem, expected) => {
    expect(classifyResourceError(new ApiError(status, problem as never))).toBe(expected);
  });

  it('keeps non-API failures generic', () => {
    expect(classifyResourceError(new Error('network'))).toBe('other');
  });
});
