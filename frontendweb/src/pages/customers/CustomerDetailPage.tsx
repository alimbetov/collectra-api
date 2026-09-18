import { useQuery } from '@tanstack/react-query';
import { Link, Navigate, useParams } from 'react-router-dom';
import { customerQueries } from '../../entities/customer/api/customer.queries';
import { isCustomerId } from '../../features/customer-detail/model/customer-detail';
import { CustomerEmails, CustomerPhones } from '../../features/customer-detail/ui/CustomerContacts';
import { CustomerDetailHeader } from '../../features/customer-detail/ui/CustomerDetailHeader';
import { CustomerDetailTabs } from '../../features/customer-detail/ui/CustomerDetailTabs';
import { CustomerOverview } from '../../features/customer-detail/ui/CustomerOverview';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { EmptyState, Spinner } from '../../shared/ui';

export type CustomerDetailTab = 'overview' | 'contacts';

function status(error: unknown): number | null {
  return error instanceof ApiError ? error.status : null;
}

export function CustomerDetailPage({ tab }: { tab: CustomerDetailTab }) {
  const { customerId } = useParams();
  const { t } = useI18n();
  const validId = isCustomerId(customerId);
  const id = validId ? customerId : '';
  const detail = useQuery({ ...customerQueries.detail(id), enabled: validId });
  const emails = useQuery({ ...customerQueries.emails(id), enabled: validId && tab === 'contacts' });
  const phones = useQuery({ ...customerQueries.phones(id), enabled: validId && tab === 'contacts' });
  const errors = [detail.error, emails.error, phones.error];

  if (errors.some((error) => status(error) === 403)) return <Navigate to="/forbidden" replace />;
  if (!validId || errors.some((error) => status(error) === 404)) {
    return (
      <div className="customer-detail customer-detail__not-found">
        <EmptyState title={t('customerDetail.notFound')} description={t('customerDetail.notFoundDescription')} />
        <Link to="/customers">{t('customerDetail.back')}</Link>
      </div>
    );
  }
  if (detail.isLoading) {
    return <div className="customer-detail__loading"><Spinner label={t('customerDetail.loading')} /></div>;
  }
  if (detail.error) {
    return <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} />;
  }
  if (!detail.data) return null;

  return (
    <div className="customer-detail">
      <CustomerDetailHeader customer={detail.data} />
      <CustomerDetailTabs customerId={id} />
      {tab === 'overview' ? <CustomerOverview customer={detail.data} /> : (
        <div className="customer-detail-grid">
          {emails.isLoading ? <div className="customer-detail-card"><Spinner label={t('customerDetail.loadingEmails')} /></div> : null}
          {emails.error ? <ProblemDetailPanel error={emails.error} onRetry={() => void emails.refetch()} /> : null}
          {emails.data ? <CustomerEmails values={emails.data} /> : null}
          {phones.isLoading ? <div className="customer-detail-card"><Spinner label={t('customerDetail.loadingPhones')} /></div> : null}
          {phones.error ? <ProblemDetailPanel error={phones.error} onRetry={() => void phones.refetch()} /> : null}
          {phones.data ? <CustomerPhones values={phones.data} /> : null}
        </div>
      )}
    </div>
  );
}
