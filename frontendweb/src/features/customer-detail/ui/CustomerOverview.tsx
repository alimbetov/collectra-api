import type { CustomerDetailDto } from '../../../entities/customer/model/customer.types';
import { formatInstant } from '../../../shared/i18n/formatters';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Button } from '../../../shared/ui';
import { customFieldsText } from '../model/customer-detail';

interface FieldProps {
  label: string;
  value: string | null | undefined;
}

function Field({ label, value }: FieldProps) {
  const { t } = useI18n();
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value || t('customers.missing')}</dd>
    </div>
  );
}

export function CustomerOverview({ customer, onManageSegments, canManage }: { customer: CustomerDetailDto; onManageSegments: () => void; canManage: boolean }) {
  const { t, locale, timeZone } = useI18n();
  const customFields = customFieldsText(customer.customFields);
  return (
    <div className="customer-detail-grid">
      <section className="customer-detail-card">
        <h2>{t('customerDetail.identity')}</h2>
        <dl className="customer-detail-fields">
          <Field label={t('customers.filters.externalId')} value={customer.externalId} />
          <Field label={t('customers.columns.type')} value={t(`customers.type.${customer.customerType}`)} />
          <Field label={t('customerDetail.companyName')} value={customer.companyName} />
          <Field label={t('customerDetail.lastName')} value={customer.lastName} />
          <Field label={t('customerDetail.firstName')} value={customer.firstName} />
          <Field label={t('customerDetail.middleName')} value={customer.middleName} />
        </dl>
      </section>

      <section className="customer-detail-card">
        <h2>{t('customerDetail.preferences')}</h2>
        <dl className="customer-detail-fields">
          <Field label={t('customers.columns.manager')} value={customer.managerDisplayName} />
          <Field label={t('customerDetail.locale')} value={customer.preferredLocale} />
          <Field label={t('customerDetail.timezone')} value={customer.timezone} />
          <Field label={t('customerDetail.created')} value={formatInstant(customer.createdAt, locale, timeZone)} />
          <Field label={t('customers.columns.updated')} value={formatInstant(customer.updatedAt, locale, timeZone)} />
        </dl>
      </section>

      <section className="customer-detail-card">
        <div className="customer-detail-card__heading">
          <h2>{t('customers.columns.segments')}</h2>
          {canManage ? <Button variant="secondary" onClick={onManageSegments}>{t('segments.membershipAction')}</Button> : null}
        </div>
        {customer.segments.length ? (
          <div className="customer-segments">
            {customer.segments.map((segment) => <span key={segment.id}>{segment.name}</span>)}
          </div>
        ) : <p>{t('customerDetail.noSegments')}</p>}
      </section>

      <section className="customer-detail-card">
        <h2>{t('customerDetail.customFields')}</h2>
        {customFields ? <pre className="customer-custom-fields">{customFields}</pre> : <p>{t('customerDetail.noCustomFields')}</p>}
      </section>
    </div>
  );
}
