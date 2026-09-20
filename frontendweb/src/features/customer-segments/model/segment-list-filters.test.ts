import { describe, expect, it } from 'vitest';
import { parseSegmentListState, serializeSegmentListState, toSegmentListQuery, updateSegmentListState } from './segment-list-filters';

describe('segment list URL state', () => {
  it('canonicalizes unsupported values and round-trips supported filters', () => {
    expect(parseSegmentListState(new URLSearchParams('page=-1&size=999&active=x&sort=bad')))
      .toMatchObject({ page: 0, size: 20, active: '', sort: 'name,asc' });
    const state = parseSegmentListState(new URLSearchParams('search=vip&active=true&page=2&size=50&sort=code%2Cdesc'));
    expect(serializeSegmentListState(state).toString()).toBe('search=vip&active=true&page=2&size=50&sort=code%2Cdesc');
    expect(toSegmentListQuery(state)).toEqual({ search: 'vip', active: true, page: 2, size: 50, sort: 'code,desc' });
  });

  it('resets page when a business filter changes', () => {
    const state = parseSegmentListState(new URLSearchParams('page=4'));
    expect(updateSegmentListState(state, { active: 'false' }).page).toBe(0);
  });

  it('always sends paging defaults because backend defaults differ', () => {
    expect(toSegmentListQuery(parseSegmentListState(new URLSearchParams())))
      .toEqual({ page: 0, size: 20, sort: 'name,asc' });
  });
});
