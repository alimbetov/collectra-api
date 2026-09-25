import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { receivableQueries } from '../../entities/receivable/api/receivable.queries';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant, formatLocalDate, formatMoney } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Pagination, Spinner, StatusBadge } from '../../shared/ui';
import { useState } from 'react';

export function InvoiceDetailPage() {
  const { invoiceId = '' } = useParams();
  const { locale, timeZone } = useI18n();
  const [page, setPage] = useState(0);
  const detail = useQuery({ ...receivableQueries.invoice(invoiceId), enabled: Boolean(invoiceId) });
  const allocations = useQuery({ ...receivableQueries.allocations(invoiceId, page), enabled: Boolean(detail.data) });
  if (detail.isLoading) return <Spinner label="Загружаем счёт…" />;
  if (detail.error) return <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} />;
  const x = detail.data; if (!x) return null;
  return <div className="customer-detail">
    <header className="customer-detail__header"><div><Link to="/receivables">← Задолженности</Link><p className="eyebrow">{x.externalId}</p><h1>{x.invoiceNumber}</h1></div><StatusBadge tone={x.paymentStatus === 'PAID' ? 'success' : x.overdue ? 'danger' : 'warning'}>{x.paymentStatus}</StatusBadge></header>
    <div className="customer-detail-grid">
      <section className="customer-detail-card"><h2>Суммы</h2><dl className="customer-detail-fields"><div><dt>Начислено</dt><dd>{formatMoney(x.originalAmount,x.currency,locale)}</dd></div><div><dt>Оплачено</dt><dd>{formatMoney(x.paidAmount,x.currency,locale)}</dd></div><div><dt>Остаток</dt><dd>{formatMoney(x.outstandingAmount,x.currency,locale)}</dd></div></dl></section>
      <section className="customer-detail-card"><h2>Сроки</h2><dl className="customer-detail-fields"><div><dt>Срок оплаты</dt><dd>{formatLocalDate(x.dueDate,locale)}</dd></div><div><dt>Business date</dt><dd>{formatLocalDate(x.businessDate,locale)}</dd></div><div><dt>Просрочка</dt><dd>{x.overdue ? `${x.daysOverdue} дн.` : 'Нет'}</dd></div></dl></section>
      <section className="customer-detail-card"><h2>Связи</h2><p><Link to={`/customers/${x.customerId}`}>Клиент</Link></p>{x.contractId ? <p><Link to={`/contracts/${x.contractId}`}>Договор</Link></p> : null}</section>
      <section className="customer-detail-card"><h2>Аудит</h2><dl className="customer-detail-fields"><div><dt>Создан</dt><dd>{formatInstant(x.createdAt,locale,timeZone)}</dd></div><div><dt>Обновлён</dt><dd>{formatInstant(x.updatedAt,locale,timeZone)}</dd></div></dl></section>
    </div>
    <section className="customer-detail-card"><h2>Распределения платежей</h2>{allocations.isLoading ? <Spinner label="Загружаем распределения…" /> : null}{allocations.error ? <ProblemDetailPanel error={allocations.error} onRetry={() => void allocations.refetch()} /> : null}{allocations.data ? <><table><thead><tr><th>Дата</th><th>Платёж</th><th>Сумма</th><th>Статус</th></tr></thead><tbody>{allocations.data.items.map(a => <tr key={a.id}><td>{formatInstant(a.createdAt,locale,timeZone)}</td><td>{a.paymentId}</td><td>{formatMoney(a.amount,x.currency,locale)}</td><td>{a.status}</td></tr>)}</tbody></table><Pagination page={allocations.data.page} totalPages={allocations.data.totalPages} onPageChange={setPage} label="История распределений" previousLabel="Назад" nextLabel="Далее" /></> : null}</section>
  </div>;
}
