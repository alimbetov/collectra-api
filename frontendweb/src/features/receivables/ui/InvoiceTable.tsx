import { Link } from 'react-router-dom';
import type { InvoiceItemDto } from '../../../entities/receivable/model/receivable.types';
import { formatLocalDate, formatMoney } from '../../../shared/i18n/formatters';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { DataTable, StatusBadge } from '../../../shared/ui';

export function InvoiceTable({ rows }: { rows: readonly InvoiceItemDto[] }) {
  const { locale } = useI18n();
  const columns = [
    { key: 'invoice', header: 'Счёт', render: (r: InvoiceItemDto) => <div className="customer-name-cell"><Link to={`/receivables/invoices/${r.id}`}>{r.invoiceNumber}</Link><span>{r.externalId}</span></div> },
    { key: 'customer', header: 'Клиент', render: (r: InvoiceItemDto) => <Link to={`/customers/${r.customerId}`}>{r.customerDisplayName ?? r.customerId}</Link> },
    { key: 'contract', header: 'Договор', render: (r: InvoiceItemDto) => r.contractId ? <Link to={`/contracts/${r.contractId}`}>{r.contractNumber ?? r.contractId}</Link> : '—' },
    { key: 'due', header: 'Срок', render: (r: InvoiceItemDto) => formatLocalDate(r.dueDate, locale) },
    { key: 'outstanding', header: 'Остаток', render: (r: InvoiceItemDto) => formatMoney(r.outstandingAmount, r.currency, locale) },
    { key: 'status', header: 'Статус', render: (r: InvoiceItemDto) => <StatusBadge tone={r.paymentStatus === 'PAID' ? 'success' : r.overdue ? 'danger' : 'warning'}>{r.paymentStatus}</StatusBadge> },
  ];
  return <DataTable rows={rows} rowKey={(r) => r.id} columns={columns} caption="Счета к получению" emptyTitle="Счета не найдены" emptyDescription="Измените фильтры или создайте новый счёт." />;
}
