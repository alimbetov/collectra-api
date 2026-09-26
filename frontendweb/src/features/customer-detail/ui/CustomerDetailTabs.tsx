import { NavLink } from 'react-router-dom';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { useAuth } from '../../../features/auth/model/auth-context';

export function CustomerDetailTabs({ customerId }: { customerId: string }) {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  return (
    <nav className="customer-detail-tabs" aria-label={t('customerDetail.tabs.label')}>
      <NavLink end to={`/customers/${customerId}`}>
        {t('customerDetail.tabs.overview')}
      </NavLink>
      <NavLink to={`/customers/${customerId}/contacts`}>
        {t('customerDetail.tabs.contacts')}
      </NavLink>
      {hasPermission('CONTRACT_READ') ? <NavLink to={`/customers/${customerId}/contracts`}>
        {t('customerDetail.tabs.contracts')}
      </NavLink> : null}
    </nav>
  );
}
