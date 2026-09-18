import { describe, expect, it } from 'vitest';
import { customFieldsText, isCustomerId, orderCustomerEmails, orderCustomerPhones } from './customer-detail';

describe('customer detail model', () => {
  it('accepts canonical UUIDs and rejects malformed route IDs', () => {
    expect(isCustomerId('11111111-1111-4111-8111-111111111111')).toBe(true);
    expect(isCustomerId('../foreign')).toBe(false);
    expect(isCustomerId(undefined)).toBe(false);
  });

  it('orders contacts by primary, active state, value and stable ID without mutation', () => {
    const emails = [
      { id: 'b', email: 'z@test.kz', type: 'WORK', primary: false, verified: false, status: 'ACTIVE' as const, version: 0 },
      { id: 'c', email: 'a@test.kz', type: 'HOME', primary: false, verified: false, status: 'INACTIVE' as const, version: 1 },
      { id: 'a', email: 'main@test.kz', type: 'WORK', primary: true, verified: true, status: 'ACTIVE' as const, version: 2 },
    ];
    const phones = [
      { id: 'b', phone: '8 701 222', normalizedPhone: '+8701222', type: 'MOBILE', primary: false, verified: false, status: 'INACTIVE' as const, version: 0 },
      { id: 'a', phone: '+7 701 111', normalizedPhone: '+7701111', type: 'MOBILE', primary: false, verified: false, status: 'ACTIVE' as const, version: 0 },
    ];

    expect(orderCustomerEmails(emails).map((value) => value.id)).toEqual(['a', 'b', 'c']);
    expect(orderCustomerPhones(phones).map((value) => value.id)).toEqual(['a', 'b']);
    expect(emails.map((value) => value.id)).toEqual(['b', 'c', 'a']);
  });

  it('serializes custom fields as escaped text input and handles missing data', () => {
    expect(customFieldsText({ note: '<script>alert(1)</script>' })).toContain('<script>');
    expect(customFieldsText(null)).toBeNull();
  });
});
