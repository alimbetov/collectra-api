import { describe, expect, it } from 'vitest';
import {
  formatInstant,
  formatLocalDate,
  formatMoney,
  resolveLocale,
  resolveTimeZone,
} from './formatters';

describe('locale and timezone boundaries', () => {
  it('supports ru/kk variants and falls back to ru', () => {
    expect(resolveLocale('kk-KZ')).toBe('kk');
    expect(resolveLocale('ru_RU')).toBe('ru');
    expect(resolveLocale('en-US')).toBe('ru');
  });

  it('accepts IANA timezones and falls back to UTC', () => {
    expect(resolveTimeZone('Asia/Almaty')).toBe('Asia/Almaty');
    expect(resolveTimeZone('not/a-zone')).toBe('UTC');
  });
});

describe('Intl formatters', () => {
  it('formats LocalDate without applying the user timezone', () => {
    expect(formatLocalDate('2026-01-02', 'ru')).toContain('2026');
    expect(formatLocalDate('invalid', 'ru')).toBe('—');
  });

  it('applies the user timezone to instants', () => {
    const utc = formatInstant('2026-01-02T00:00:00Z', 'ru', 'UTC');
    const almaty = formatInstant('2026-01-02T00:00:00Z', 'ru', 'Asia/Almaty');
    expect(utc).not.toBe(almaty);
  });

  it('rejects invalid monetary inputs', () => {
    expect(formatMoney('1200', 'KZT', 'kk')).not.toBe('—');
    expect(formatMoney('NaN', 'KZT', 'kk')).toBe('—');
    expect(formatMoney('1200', 'tenge', 'kk')).toBe('—');
  });

  it('formats NUMERIC(19,4) boundaries without converting money to number', () => {
    const value = '999999999999999.9999';
    const ru = formatMoney(value, 'KZT', 'ru');
    const kk = formatMoney(value, 'KZT', 'kk');

    expect(ru).not.toBe('—');
    expect(kk).not.toBe('—');
    expect(ru.replace(/[^0-9]/g, '')).toContain('9999999999999999999');
    expect(kk.replace(/[^0-9]/g, '')).toContain('9999999999999999999');
  });

  it('rejects exponent, localized, over-scale and over-precision money', () => {
    expect(formatMoney('1e3', 'KZT', 'ru')).toBe('—');
    expect(formatMoney('1,25', 'KZT', 'ru')).toBe('—');
    expect(formatMoney('1.00001', 'KZT', 'ru')).toBe('—');
    expect(formatMoney('1000000000000000', 'KZT', 'ru')).toBe('—');
  });
});
