import type { CustomerEmailDto, CustomerPhoneDto } from '../../../entities/customer/model/customer.types';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Button, EmptyState, StatusBadge } from '../../../shared/ui';
import { orderCustomerEmails, orderCustomerPhones } from '../model/customer-detail';

function marker(value: boolean, yes: string, no: string) {
  return value ? yes : no;
}

function typeLabel(type: string, t: ReturnType<typeof useI18n>['t']): string {
  const normalized = type.trim().toUpperCase();
  if (normalized === 'WORK') return t('customerDetail.contactType.WORK');
  if (normalized === 'HOME') return t('customerDetail.contactType.HOME');
  if (normalized === 'MOBILE') return t('customerDetail.contactType.MOBILE');
  if (normalized === 'OTHER') return t('customerDetail.contactType.OTHER');
  return type || t('customerDetail.contactType.OTHER');
}

export function CustomerEmails({ values, onAdd, onEdit, canManage }: {
  values: readonly CustomerEmailDto[];
  onAdd: () => void;
  onEdit: (contact: CustomerEmailDto) => void;
  canManage: boolean;
}) {
  const { t } = useI18n();
  const rows = orderCustomerEmails(values);
  return (
    <section className="customer-detail-card customer-contact-section">
      <div className="customer-contact-section__header"><h2>{t('customerDetail.emails')}</h2>{canManage ? <Button variant="secondary" onClick={onAdd}>{t('contactEdit.addEmail')}</Button> : null}</div>
      {!rows.length ? <EmptyState title={t('customerDetail.noEmails')} /> : (
        <div className="ui-data-table-scroll">
          <table className="ui-data-table" aria-label={t('customerDetail.emails')}>
            <thead><tr><th scope="col">{t('customers.columns.email')}</th><th scope="col">{t('customerDetail.contactType')}</th><th scope="col">{t('customers.columns.status')}</th><th scope="col">{t('customerDetail.flags')}</th><th scope="col">{t('contactEdit.actions')}</th></tr></thead>
            <tbody>{rows.map((email) => (
              <tr key={email.id}>
                <td className="customer-contact-value">{email.email}</td>
                <td>{typeLabel(email.type, t)}</td>
                <td><StatusBadge tone={email.status === 'ACTIVE' ? 'success' : 'neutral'}>{t(`customerDetail.contactStatus.${email.status}`)}</StatusBadge></td>
                <td>{marker(email.primary, t('customerDetail.primary'), t('customerDetail.notPrimary'))}; {marker(email.verified, t('customerDetail.verified'), t('customerDetail.notVerified'))}</td>
                <td>{canManage ? <Button variant="secondary" onClick={() => onEdit(email)}>{t('contactEdit.edit')}</Button> : null}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function CustomerPhones({ values, onAdd, onEdit, canManage }: {
  values: readonly CustomerPhoneDto[];
  onAdd: () => void;
  onEdit: (contact: CustomerPhoneDto) => void;
  canManage: boolean;
}) {
  const { t } = useI18n();
  const rows = orderCustomerPhones(values);
  return (
    <section className="customer-detail-card customer-contact-section">
      <div className="customer-contact-section__header"><h2>{t('customerDetail.phones')}</h2>{canManage ? <Button variant="secondary" onClick={onAdd}>{t('contactEdit.addPhone')}</Button> : null}</div>
      {!rows.length ? <EmptyState title={t('customerDetail.noPhones')} /> : (
        <div className="ui-data-table-scroll">
          <table className="ui-data-table" aria-label={t('customerDetail.phones')}>
            <thead><tr><th scope="col">{t('customers.columns.phone')}</th><th scope="col">{t('customerDetail.contactType')}</th><th scope="col">{t('customers.columns.status')}</th><th scope="col">{t('customerDetail.flags')}</th><th scope="col">{t('contactEdit.actions')}</th></tr></thead>
            <tbody>{rows.map((phone) => (
              <tr key={phone.id}>
                <td className="customer-contact-value">{phone.phone}</td>
                <td>{typeLabel(phone.type, t)}</td>
                <td><StatusBadge tone={phone.status === 'ACTIVE' ? 'success' : 'neutral'}>{t(`customerDetail.contactStatus.${phone.status}`)}</StatusBadge></td>
                <td>{marker(phone.primary, t('customerDetail.primary'), t('customerDetail.notPrimary'))}; {marker(phone.verified, t('customerDetail.verified'), t('customerDetail.notVerified'))}</td>
                <td>{canManage ? <Button variant="secondary" onClick={() => onEdit(phone)}>{t('contactEdit.edit')}</Button> : null}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}
