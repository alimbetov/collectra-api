import { createContext, useContext, useMemo, type PropsWithChildren } from 'react';
import { useAuth } from '../../features/auth/model/auth-context';
import { kkMessages, ruMessages, type MessageKey } from './messages';
import { resolveLocale, resolveTimeZone, type AppLocale } from './formatters';

interface I18nContextValue {
  locale: AppLocale;
  timeZone: string;
  t: (key: MessageKey) => string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: PropsWithChildren) {
  const { user } = useAuth();
  const locale = resolveLocale(user?.locale);
  const timeZone = resolveTimeZone(user?.timezone);

  const value = useMemo<I18nContextValue>(() => {
    const messages = locale === 'kk' ? kkMessages : ruMessages;
    return { locale, timeZone, t: (key) => messages[key] ?? ruMessages[key] };
  }, [locale, timeZone]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nContextValue {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error('useI18n must be used inside I18nProvider');
  }
  return context;
}
