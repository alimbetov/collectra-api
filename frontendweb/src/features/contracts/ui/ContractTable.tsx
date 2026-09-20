import { Link } from 'react-router-dom';
import type { ContractListItemDto, ContractStatus } from '../../../entities/contract/model/contract.types';
import { formatLocalDate } from '../../../shared/i18n/formatters';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { DataTable, StatusBadge } from '../../../shared/ui';

const tones: Record<ContractStatus, 'success' | 'warning' | 'neutral' | 'danger'> = {
  ACTIVE: 'success', SUSPENDED: 'warning', CLOSED: 'neutral', CANCELLED: 'danger',
};

export function ContractTable({ rows, showCustomer = true }: { rows: readonly ContractListItemDto[]; showCustomer?: boolean }) {
  const { t, locale } = useI18n();
  const columns = [
    { key: 'contract', header: t('contracts.number'), render: (row: ContractListItemDto) => <div className="customer-name-cell"><Link to={`/contracts/${row.id}`}>{row.contractNumber}</Link><span>{row.externalId}</span></div> },
    ...(showCustomer ? [{ key: 'customer', header: t('contracts.customer'), render: (row: ContractListItemDto) => <div className="customer-name-cell"><Link to={`/customers/${row.customerId}`}>{row.customerDisplayName ?? row.customerExternalId ?? row.customerId}</Link><span>{row.customerExternalId}</span></div> }] : []),
    { key: 'status', header: t('contracts.status'), render: (row: ContractListItemDto) => <StatusBadge tone={tones[row.status]}>{t(`contracts.status.${row.status}`)}</StatusBadge> },
    { key: 'validity', header: t('contracts.validity'), render: (row: ContractListItemDto) => `${formatLocalDate(row.validFrom, locale)} — ${row.validTo ? formatLocalDate(row.validTo, locale) : t('contracts.openEnded')}` },
    { key: 'renewal', header: t('contracts.renewalDate'), render: (row: ContractListItemDto) => row.renewalDate ? formatLocalDate(row.renewalDate, locale) : '—' },
  ];
  return <DataTable rows={rows} rowKey={(row) => row.id} columns={columns} caption={t('contracts.tableCaption')} emptyTitle={t('contracts.empty')} emptyDescription={t('contracts.emptyDescription')} />;
}
