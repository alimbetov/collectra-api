import type { PropsWithChildren, ReactNode } from 'react';
import { ProblemDetailPanel } from '../../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { formatInstant, formatLocalDate } from '../../../shared/i18n/formatters';
import { Spinner } from '../../../shared/ui';

interface DashboardCardProps extends PropsWithChildren {
  title: ReactNode;
  asOf?: string;
  businessDate?: string;
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  action?: ReactNode;
}

export function DashboardCard({
  title,
  asOf,
  businessDate,
  loading = false,
  error,
  onRetry,
  action,
  children,
}: DashboardCardProps) {
  const { t, locale, timeZone } = useI18n();
  return (
    <section className="dashboard-card">
      <header>
        <h2>{title}</h2>
        {action}
      </header>
      {loading ? <Spinner label={t('dashboard.loading')} /> : null}
      {!loading && error ? <ProblemDetailPanel error={error} onRetry={onRetry} /> : null}
      {!loading && !error ? children : null}
      {!loading && !error && asOf && businessDate ? (
        <footer>
          {t('dashboard.asOf')}: {formatInstant(asOf, locale, timeZone)} · {t('dashboard.businessDate')}:{' '}
          {formatLocalDate(businessDate, locale)}
        </footer>
      ) : null}
    </section>
  );
}
