export type AppLocale = 'ru' | 'kk';

const intlLocales: Record<AppLocale, string> = { ru: 'ru-KZ', kk: 'kk-KZ' };

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

export function formatMoney(value: number, currency: string, locale: AppLocale): string {
  if (!Number.isFinite(value) || !/^[A-Z]{3}$/.test(currency)) return '—';
  try {
    return new Intl.NumberFormat(intlLocales[locale], { style: 'currency', currency }).format(value);
  } catch {
    return '—';
  }
}
