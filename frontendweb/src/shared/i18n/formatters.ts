import type { DecimalString } from '../api/contracts';

export type AppLocale = 'ru' | 'kk';

const intlLocales: Record<AppLocale, string> = { ru: 'ru-KZ', kk: 'kk-KZ' };
const decimalPattern = /^(-?)(0|[1-9][0-9]{0,14})(?:\.([0-9]{1,4}))?$/;

export function resolveLocale(value: string | null | undefined): AppLocale {
  const language = value?.trim().toLowerCase().split(/[-_]/)[0];
  return language === 'kk' ? 'kk' : 'ru';
}

export function resolveTimeZone(value: string | null | undefined): string {
  if (!value?.trim()) return 'UTC';
  try {
    new Intl.DateTimeFormat('en', { timeZone: value }).format();
    return value;
  } catch {
    return 'UTC';
  }
}

export function formatLocalDate(value: string, locale: AppLocale): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return '—';
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), 12));
  if (Number.isNaN(date.valueOf())) return '—';
  return new Intl.DateTimeFormat(intlLocales[locale], { dateStyle: 'medium', timeZone: 'UTC' }).format(date);
}

export function formatInstant(value: string, locale: AppLocale, timeZone: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return '—';
  return new Intl.DateTimeFormat(intlLocales[locale], {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: resolveTimeZone(timeZone),
  }).format(date);
}

export function formatNumber(value: number, locale: AppLocale): string {
  return Number.isFinite(value) ? new Intl.NumberFormat(intlLocales[locale]).format(value) : '—';
}

export function formatMoney(value: DecimalString, currency: string, locale: AppLocale): string {
  const match = decimalPattern.exec(value);
  if (!match || !/^[A-Z]{3}$/.test(currency)) return '—';
  try {
    const defaultFormatter = new Intl.NumberFormat(intlLocales[locale], {
      style: 'currency',
      currency,
    });
    const fraction = match[3] ?? '';
    const scale = Math.max(
      defaultFormatter.resolvedOptions().minimumFractionDigits ?? 0,
      fraction.length,
    );
    const formatter = new Intl.NumberFormat(intlLocales[locale], {
      style: 'currency',
      currency,
      minimumFractionDigits: scale,
      maximumFractionDigits: scale,
    });
    const integer = BigInt(`${match[1]}${match[2]}`);
    const paddedFraction = fraction.padEnd(scale, '0');
    return formatter
      .formatToParts(integer)
      .map((part) => (part.type === 'fraction' ? paddedFraction : part.value))
      .join('');
  } catch {
    return '—';
  }
}
