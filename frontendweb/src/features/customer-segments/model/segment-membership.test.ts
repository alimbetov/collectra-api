import { describe, expect, it } from 'vitest';
import { membershipDelta } from './segment-membership';

describe('segment membership delta', () => {
  it('sends only additions and removals in deterministic order', () => {
    expect(membershipDelta(['b', 'a'], new Set(['b', 'c']))).toEqual({ additions: ['c'], removals: ['a'] });
  });
});
