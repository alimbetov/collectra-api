import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useParams } from 'react-router-dom';
import { campaignQueries } from '../../entities/campaign/api/campaign.queries';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import { DataTable, EmptyState, Spinner, StatusBadge } from '../../shared/ui';

export function CampaignRunPage() {
  const { campaignId = '', runId = '' } = useParams();
  const { t, locale, timeZone } = useI18n();
  const run = useQuery(campaignQueries.run(campaignId, runId));
  const recipients = useQuery(campaignQueries.recipients(campaignId, runId, 0));

  if (run.error instanceof ApiError && run.error.status === 403) {
    return <Navigate to="/forbidden" replace />;
  }
  if (run.error instanceof ApiError && run.error.status === 404) {
    return (
      <div className="customer-detail">
        <EmptyState title={t('campaigns.noRuns')} />
        <Link to={`/campaigns/${campaignId}`}>{t('campaigns.back')}</Link>
      </div>
    );
  }
  if (run.isLoading) return <Spinner label={t('campaigns.loading')} />;
  if (run.error) return <ProblemDetailPanel error={run.error} onRetry={() => void run.refetch()} />;
  if (!run.data) return null;

  const value = run.data;
  const columns = [
    {
      key: 'status',
      header: t('campaigns.status'),
      render: (row: NonNullable<typeof recipients.data>['items'][number]) => (
        <StatusBadge tone={row.status === 'SENT' ? 'success' : row.status === 'FAILED' ? 'danger' : 'neutral'}>
          {row.status}
        </StatusBadge>
      ),
    },
    {
      key: 'destination',
      header: t('campaigns.destination'),
      render: (row: NonNullable<typeof recipients.data>['items'][number]) => row.destination,
    },
    {
      key: 'channel',
      header: t('campaigns.channel'),
      render: (row: NonNullable<typeof recipients.data>['items'][number]) => row.channel,
    },
    {
      key: 'locale',
      header: t('campaigns.locale'),
      render: (row: NonNullable<typeof recipients.data>['items'][number]) => row.locale ?? '—',
    },
    {
      key: 'reason',
      header: t('campaigns.validationErrors'),
      render: (row: NonNullable<typeof recipients.data>['items'][number]) => row.skipReason ?? '—',
    },
  ];

  return (
    <div className="customer-detail campaign-run-detail">
      <header className="customer-detail__header">
        <div>
          <Link className="customer-detail__back" to={`/campaigns/${campaignId}`}>
            ← {t('campaigns.detail')}
          </Link>
          <p className="eyebrow">{t('campaigns.runDetail')}</p>
          <h1>{value.id}</h1>
        </div>
        <StatusBadge tone={value.status === 'COMPLETED' ? 'success' : 'info'}>{value.status}</StatusBadge>
      </header>

      <section className="platform-kpi-grid">
        <Kpi label={t('campaigns.recipientCount')} value={value.recipientCount} />
        <Kpi label={t('campaigns.sent')} value={value.sentCount} />
        <Kpi label={t('campaigns.failed')} value={value.failedCount} />
        <Kpi label={t('campaigns.skipped')} value={value.skippedCount} />
        <Kpi label={t('campaigns.pending')} value={value.pendingCount} />
        <Kpi label={t('campaigns.retry')} value={value.retryCount} />
      </section>

      <section className="customer-detail-card">
        <dl className="customer-detail-fields">
          <div><dt>{t('campaigns.updated')}</dt><dd>{formatInstant(value.updatedAt, locale, timeZone)}</dd></div>
          <div><dt>{t('campaigns.revision')}</dt><dd>{value.version}</dd></div>
        </dl>
      </section>

      <section className="customer-detail-card">
        <h2>{t('campaigns.recipients')}</h2>
        {recipients.error ? <ProblemDetailPanel error={recipients.error} onRetry={() => void recipients.refetch()} /> : null}
        {recipients.data ? (
          <DataTable
            columns={columns}
            rows={recipients.data.items}
            rowKey={(row) => row.id}
            emptyTitle={t('campaigns.empty')}
          />
        ) : null}
      </section>
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: number }) {
  return <article className="platform-kpi"><span>{label}</span><strong>{value}</strong></article>;
}
