import { describe, expect, it } from 'vitest';
import {
  hasValidContractRanges, parseContractListState, serializeContractListState,
  toContractListQuery, updateContractListState,
} from './contract-list-filters';

describe('contract list URL state', () => {
  it('canonicalizes values and sends explicit paging defaults', () => {
    const state = parseContractListState(new URLSearchParams('status=BAD&page=-1&size=500&sort=bad'));
    expect(state).toMatchObject({ status: '', page: 0, size: 20, sort: 'createdAt,desc' });
    expect(toContractListQuery(state)).toEqual({ page: 0, size: 20, sort: 'createdAt,desc' });
  });

  it('round-trips filters and converts created calendar dates to UTC bounds', () => {
    const state = parseContractListState(new URLSearchParams('status=ACTIVE&createdFrom=2026-01-01&createdTo=2026-01-31&page=2'));
    expect(serializeContractListState(state).toString()).toBe('status=ACTIVE&createdFrom=2026-01-01&createdTo=2026-01-31&page=2');
    expect(toContractListQuery(state)).toMatchObject({ createdFrom: '2026-01-01T00:00:00.000Z', createdTo: '2026-01-31T23:59:59.999Z' });
  });

  it('resets page and rejects inverted date ranges', () => {
    const state = parseContractListState(new URLSearchParams('page=4&validFrom=2026-05-01&validTo=2026-04-01'));
    expect(hasValidContractRanges(state)).toBe(false);
    expect(updateContractListState(state, { status: 'CLOSED' }).page).toBe(0);
  });
});
