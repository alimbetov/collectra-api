import { Link } from 'react-router-dom';
import type { CustomerDetailDto, CustomerStatus } from '../../../entities/customer/model/customer.types';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { StatusBadge } from '../../../shared/ui';
import { Button } from '../../../shared/ui';

const statusTones: Record<CustomerStatus, 'success' | 'neutral' | 'warning' | 'danger'> = {
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  BLOCKED: 'danger',
  ARCHIVED: 'warning',
};

interface Props {
  customer: CustomerDetailDto;
  onEdit: () => void;
  onChangeStatus: () => void;
}

export function CustomerDetailHeader({ customer, onEdit, onChangeStatus }: Props) {
  const { t } = useI18n();
  return (
    <header className="customer-detail__header">
      <div>
        <Link className="customer-detail__back" to="/customers">
          ← {t('customerDetail.back')}
        </Link>
        <p className="eyebrow">{customer.externalId}</p>
        <h1>{customer.displayName}</h1>
      </div>
      <div className="customer-detail__classification">
        <span>{t(`customers.type.${customer.customerType}`)}</span>
        <StatusBadge tone={statusTones[customer.status]}>
          {t(`customers.status.${customer.status}`)}
        </StatusBadge>
        <Button type="button" variant="secondary" onClick={onEdit}>{t('customerEdit.action')}</Button>
        <Button type="button" variant="secondary" onClick={onChangeStatus}>{t('customerStatus.action')}</Button>
      </div>
    </header>
  );
}
