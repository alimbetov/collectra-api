import type { PropsWithChildren } from 'react';
import { useAuth } from '../features/auth/model/auth-context';
import { I18nProvider } from '../shared/i18n/i18n-context';

export function AppI18nProvider({ children }: PropsWithChildren) {
  const { user } = useAuth();
  const requestedLocale = user?.kind === 'tenant' ? user.locale : undefined;
  const requestedTimeZone = user?.kind === 'tenant' ? user.timezone : undefined;

  return (
    <I18nProvider requestedLocale={requestedLocale} requestedTimeZone={requestedTimeZone}>
      {children}
    </I18nProvider>
  );
}
