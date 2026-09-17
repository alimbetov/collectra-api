import { Link } from 'react-router-dom';
import type {
  DashboardCollectionsDto,
  DashboardDeliveryDto,
  DashboardReceivablesDto,
  DashboardSummaryDto,
} from '../../../entities/dashboard/model/dashboard.types';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { formatMoney, formatNumber } from '../../../shared/i18n/formatters';
import { DataTable, EmptyState, type DataTableColumn } from '../../../shared/ui';
import { overdueAmount } from '../model/dashboard-view-model';

function Metrics({ values }: { values: readonly { label: string; value: number }[] }) {
  const { locale } = useI18n();
  return (
    <dl className="dashboard-metrics">
      {values.map((metric) => (
        <div key={metric.label}>
          <dt>{metric.label}</dt>
          <dd>{formatNumber(metric.value, locale)}</dd>
        </div>
      ))}
    </dl>
  );
}

export function SummaryContent({ value }: { value: DashboardSummaryDto }) {
  const { t, locale } = useI18n();
  const empty =
    value.customers === 0 &&
    value.activeContracts === 0 &&
    value.openCollectionCases === 0 &&
    value.activeCampaigns === 0 &&
    value.outstandingByCurrency.length === 0;
  if (empty) return <EmptyState title={t('dashboard.empty')} />;
  return (
    <>
      <Metrics
        values={[
          { label: t('dashboard.customers'), value: value.customers },
          { label: t('dashboard.activeContracts'), value: value.activeContracts },
          { label: t('dashboard.openCases'), value: value.openCollectionCases },
          { label: t('dashboard.activeCampaigns'), value: value.activeCampaigns },
        ]}
      />
      <ul className="dashboard-money-list">
        {value.outstandingByCurrency.map((total) => (
          <li key={total.currency}>
            <span>{total.currency}</span>
            <strong>{formatMoney(total.amount, total.currency, locale)}</strong>
          </li>
        ))}
      </ul>
    </>
  );
}

export function ReceivablesContent({ value }: { value: DashboardReceivablesDto }) {
  const { t, locale } = useI18n();
  const columns: readonly DataTableColumn<DashboardReceivablesDto['currencies'][number]>[] = [
    { key: 'currency', header: t('dashboard.currency'), render: (row) => row.currency },
    { key: 'outstanding', header: t('dashboard.outstanding'), align: 'end', render: (row) => formatMoney(row.outstanding, row.currency, locale) },
    { key: 'overdue', header: t('dashboard.overdue'), align: 'end', render: (row) => {
      const amount = overdueAmount(row.aging);
      return amount === null ? '—' : formatMoney(amount, row.currency, locale);
    } },
    { key: 'dueToday', header: t('dashboard.dueToday'), align: 'end', render: (row) => formatNumber(row.dueToday, locale) },
    { key: 'dueSoon', header: t('dashboard.dueSoon'), align: 'end', render: (row) => formatNumber(row.dueSoon, locale) },
  ];
  return <DataTable columns={columns} rows={value.currencies} rowKey={(row) => row.currency} emptyTitle={t('dashboard.emptyReceivables')} />;
}

export function ReceivablesLink() {
  const { t } = useI18n();
  return <Link to="/receivables?view=invoices&overdue=true">{t('dashboard.openOverdue')}</Link>;
}

export function DeliveryContent({ value }: { value: DashboardDeliveryDto }) {
  const { t } = useI18n();
  const metrics = [
    { label: t('dashboard.recipients'), value: value.recipients },
    { label: t('dashboard.sent'), value: value.sent },
    { label: t('dashboard.retries'), value: value.retries },
    { label: t('dashboard.failed'), value: value.failed },
    { label: t('dashboard.skipped'), value: value.skipped },
  ];
  if (metrics.every((metric) => metric.value === 0)) return <EmptyState title={t('dashboard.emptyDelivery')} />;
  return <><p className="dashboard-scope-note">{t('dashboard.deliveryCumulative')}</p><Metrics values={metrics} /></>;
}

export function CollectionsContent({ value }: { value: DashboardCollectionsDto }) {
  const { t } = useI18n();
  const metrics = [
    { label: t('dashboard.activeCases'), value: value.activeCases },
    { label: t('dashboard.overdueActions'), value: value.overdueActions },
    { label: t('dashboard.promisesDue'), value: value.activePromisesDue },
    { label: t('dashboard.promisesOverdue'), value: value.activePromisesOverdue },
    { label: t('dashboard.brokenPromises'), value: value.brokenPromises },
    { label: t('dashboard.openDisputes'), value: value.openDisputes },
  ];
  if (metrics.every((metric) => metric.value === 0)) return <EmptyState title={t('dashboard.emptyCollections')} />;
  return <Metrics values={metrics} />;
}
