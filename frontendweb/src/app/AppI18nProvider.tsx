import type { PropsWithChildren } from 'react';
import { useAuth } from '../features/auth/model/auth-context';
import { I18nProvider } from '../shared/i18n/i18n-context';

export function AppI18nProvider({ children }: PropsWithChildren) {
  const { user } = useAuth();
  return (
    <I18nProvider requestedLocale={user?.locale} requestedTimeZone={user?.timezone}>
      {children}
    </I18nProvider>
  );
}
