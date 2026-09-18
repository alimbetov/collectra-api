import type { CustomerListItemDto, CustomerStatus } from '../../../entities/customer/model/customer.types';
import { Link } from 'react-router-dom';
import { formatInstant } from '../../../shared/i18n/formatters';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { DataTable, StatusBadge, type DataTableColumn } from '../../../shared/ui';

const statusTones: Record<CustomerStatus, 'success' | 'neutral' | 'warning' | 'danger'> = {
  ACTIVE: 'success',
  INACTIVE: 'neutral',
  BLOCKED: 'danger',
  ARCHIVED: 'warning',
};

interface CustomerTableProps {
  rows: readonly CustomerListItemDto[];
  filtered: boolean;
}

export function CustomerTable({ rows, filtered }: CustomerTableProps) {
  const { t, locale, timeZone } = useI18n();
  const missing = t('customers.missing');
  const columns: readonly DataTableColumn<CustomerListItemDto>[] = [
    {
      key: 'customer',
      header: t('customers.columns.customer'),
      render: (row) => (
        <div className="customer-name-cell">
          <strong><Link to={`/customers/${row.id}`}>{row.displayName}</Link></strong>
          <span>{row.externalId}</span>
        </div>
      ),
    },
    {
      key: 'type',
      header: t('customers.columns.type'),
      render: (row) => t(`customers.type.${row.customerType}`),
    },
    {
      key: 'status',
      header: t('customers.columns.status'),
      render: (row) => (
        <StatusBadge tone={statusTones[row.status]}>
          {t(`customers.status.${row.status}`)}
        </StatusBadge>
      ),
    },
    {
      key: 'email',
      header: t('customers.columns.email'),
      render: (row) => row.primaryEmail ?? missing,
    },
    {
      key: 'phone',
      header: t('customers.columns.phone'),
      render: (row) => row.primaryPhone ?? missing,
    },
    {
      key: 'manager',
      header: t('customers.columns.manager'),
      render: (row) => row.managerDisplayName ?? missing,
    },
    {
      key: 'segments',
      header: t('customers.columns.segments'),
      render: (row) =>
        row.segments.length ? (
          <div className="customer-segments">
            {row.segments.map((segment) => (
              <span key={segment.id}>{segment.name}</span>
            ))}
          </div>
        ) : (
          missing
        ),
    },
    {
      key: 'updatedAt',
      header: t('customers.columns.updated'),
      render: (row) => formatInstant(row.updatedAt, locale, timeZone),
    },
  ];

  return (
    <DataTable
      columns={columns}
      rows={rows}
      rowKey={(row) => row.id}
      caption={t('customers.tableCaption')}
      emptyTitle={filtered ? t('customers.emptyFiltered') : t('customers.empty')}
      emptyDescription={
        filtered ? t('customers.emptyFilteredDescription') : t('customers.emptyDescription')
      }
    />
  );
}
