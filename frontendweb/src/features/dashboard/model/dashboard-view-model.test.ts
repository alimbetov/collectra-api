import { describe, expect, it } from 'vitest';
import { overdueAmount, sumDecimalStrings } from './dashboard-view-model';

describe('dashboard decimal presentation', () => {
  it('adds projected amounts without converting them to JavaScript number', () => {
    expect(sumDecimalStrings(['999999999999999', '0.0001'])).toBe('999999999999999.0001');
    expect(sumDecimalStrings(['10.1000', '2.03', '-0.13'])).toBe('12');
  });

  it('sums only overdue aging buckets inside one currency', () => {
    expect(
      overdueAmount({
        current: '500',
        days1To30: '100.25',
        days31To60: '20',
        days61To90: '0',
        days90Plus: '1.75',
      }),
    ).toBe('122');
  });

  it('fails closed for a malformed decimal projection', () => {
    expect(sumDecimalStrings(['1e3'])).toBeNull();
    expect(sumDecimalStrings(['999999999999999.9999', '0.0001'])).toBeNull();
  });
});
