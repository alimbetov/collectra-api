import type { PropsWithChildren } from 'react';
import { useI18n } from '../shared/i18n/i18n-context';
import { ToastProvider } from '../shared/ui';

export function FeedbackProvider({ children }: PropsWithChildren) {
  const { t } = useI18n();
  return <ToastProvider closeLabel={t('toast.close')}>{children}</ToastProvider>;
}
