import { describe, expect, it } from 'vitest';
import { allowedContractActions } from './contract-lifecycle';

describe('contract lifecycle UI', () => {
  it('exposes only legal commands and none for terminal states', () => {
    expect(allowedContractActions.ACTIVE).toEqual(['suspend', 'close', 'cancel']);
    expect(allowedContractActions.SUSPENDED).toEqual(['activate', 'close', 'cancel']);
    expect(allowedContractActions.CLOSED).toEqual([]);
    expect(allowedContractActions.CANCELLED).toEqual([]);
  });
});
