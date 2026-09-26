import { NavLink } from 'react-router-dom';
import { useI18n } from '../../../shared/i18n/i18n-context';

export function CustomerDetailTabs({ customerId, canReadContracts }: { customerId: string; canReadContracts: boolean }) {
  const { t } = useI18n();
  return (
    <nav className="customer-detail-tabs" aria-label={t('customerDetail.tabs.label')}>
      <NavLink end to={`/customers/${customerId}`}>
        {t('customerDetail.tabs.overview')}
      </NavLink>
      <NavLink to={`/customers/${customerId}/contacts`}>
        {t('customerDetail.tabs.contacts')}
      </NavLink>
      {canReadContracts ? <NavLink to={`/customers/${customerId}/contracts`}>
        {t('customerDetail.tabs.contracts')}
      </NavLink> : null}
    </nav>
  );
}
