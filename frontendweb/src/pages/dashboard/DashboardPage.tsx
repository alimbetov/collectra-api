import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Navigate } from 'react-router-dom';
import {
  dashboardKeys,
  dashboardQueries,
} from '../../entities/dashboard/api/dashboard.queries';
import {
  CollectionsContent,
  DeliveryContent,
  ReceivablesContent,
  ReceivablesLink,
  SummaryContent,
} from '../../features/dashboard/ui/DashboardCards';
import { DashboardCard } from '../../features/dashboard/ui/DashboardCard';
import { ApiError } from '../../shared/api/http-client';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button } from '../../shared/ui';

function forbidden(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403;
}

export function DashboardPage() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const summary = useQuery(dashboardQueries.summary());
  const receivables = useQuery(dashboardQueries.receivables());
  const delivery = useQuery(dashboardQueries.delivery());
  const collections = useQuery(dashboardQueries.collections());
  const queries = [summary, receivables, delivery, collections];

  if (queries.some((query) => forbidden(query.error))) {
    return <Navigate to="/forbidden" replace />;
  }

  const refreshing = queries.some((query) => query.isFetching);
  return (
    <div className="dashboard-page">
      <header className="dashboard-page__header">
        <div>
          <p className="eyebrow">{t('dashboard.eyebrow')}</p>
          <h1>{t('dashboard.title')}</h1>
        </div>
        <Button
          variant="secondary"
          loading={refreshing}
          onClick={() => void queryClient.invalidateQueries({ queryKey: dashboardKeys.all })}
        >
          {t('dashboard.refresh')}
        </Button>
      </header>
      <div className="dashboard-grid">
        <DashboardCard
          title={t('dashboard.summary')}
          loading={summary.isLoading}
          error={summary.error}
          onRetry={() => void summary.refetch()}
          asOf={summary.data?.asOf}
          businessDate={summary.data?.businessDate}
        >
          {summary.data ? <SummaryContent value={summary.data} /> : null}
        </DashboardCard>
        <DashboardCard
          title={t('dashboard.receivables')}
          loading={receivables.isLoading}
          error={receivables.error}
          onRetry={() => void receivables.refetch()}
          asOf={receivables.data?.asOf}
          businessDate={receivables.data?.businessDate}
          action={<ReceivablesLink />}
        >
          {receivables.data ? <ReceivablesContent value={receivables.data} /> : null}
        </DashboardCard>
        <DashboardCard
          title={t('dashboard.delivery')}
          loading={delivery.isLoading}
          error={delivery.error}
          onRetry={() => void delivery.refetch()}
          asOf={delivery.data?.asOf}
          businessDate={delivery.data?.businessDate}
        >
          {delivery.data ? <DeliveryContent value={delivery.data} /> : null}
        </DashboardCard>
        <DashboardCard
          title={t('dashboard.collections')}
          loading={collections.isLoading}
          error={collections.error}
          onRetry={() => void collections.refetch()}
          asOf={collections.data?.asOf}
          businessDate={collections.data?.businessDate}
        >
          {collections.data ? <CollectionsContent value={collections.data} /> : null}
        </DashboardCard>
      </div>
    </div>
  );
}
