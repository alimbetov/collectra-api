import { describe, expect, it } from 'vitest';
import {
  defaultCustomerListState,
  hasValidCreatedRange,
  localInputToInstant,
  parseCustomerListState,
  serializeCustomerListState,
  toCustomerListQuery,
  updateCustomerListState,
} from './customer-list-filters';

describe('customer list URL state', () => {
  it('normalizes a deep link and serializes it in a stable order', () => {
    const state = parseCustomerListState(
      new URLSearchParams(
        'sort=displayName%2Casc&page=2&search=%20Acme%20&status=ACTIVE&size=25&externalId=EXT-1',
      ),
    );

    expect(state).toMatchObject({
      search: 'Acme',
      status: 'ACTIVE',
      externalId: 'EXT-1',
      page: 2,
      size: 25,
      sort: 'displayName,asc',
    });
    expect(serializeCustomerListState(state).toString()).toBe(
      'search=Acme&status=ACTIVE&externalId=EXT-1&page=2&size=25&sort=displayName%2Casc',
    );
  });

  it('drops invalid values and omits backend defaults from the canonical URL and query', () => {
    const state = parseCustomerListState(
      new URLSearchParams('status=DELETED&managerId=nope&page=-3&size=200&sort=id%2Cdesc'),
    );

    expect(state).toEqual(defaultCustomerListState);
    expect(serializeCustomerListState(state).toString()).toBe('');
    expect(toCustomerListQuery(state)).toEqual({});
  });

  it('resets the page for filter changes but preserves an explicit page transition', () => {
    const current = { ...defaultCustomerListState, page: 4 };

    expect(updateCustomerListState(current, { status: 'BLOCKED' }).page).toBe(0);
    expect(updateCustomerListState(current, { page: 5 }, false).page).toBe(5);
  });

  it('validates instant ranges and converts local date-time using the tenant timezone', () => {
    expect(localInputToInstant('2026-09-18T12:30', 'Asia/Almaty')).toBe(
      '2026-09-18T07:30:00.000Z',
    );
    expect(
      hasValidCreatedRange({
        ...defaultCustomerListState,
        createdFrom: '2026-09-19T00:00:00.000Z',
        createdTo: '2026-09-18T00:00:00.000Z',
      }),
    ).toBe(false);
  });
});
