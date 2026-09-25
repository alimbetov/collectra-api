import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { receivableQueries } from '../../entities/receivable/api/receivable.queries';
import type { PaymentStatus } from '../../entities/receivable/model/receivable.types';
import { parseInvoiceListState, serializeInvoiceListState, toInvoiceListQuery, updateInvoiceListState, validInvoiceRanges, defaultInvoiceListState, type InvoiceListState } from '../../features/receivables/model/invoice-list-filters';
import { InvoiceTable } from '../../features/receivables/ui/InvoiceTable';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatLocalDate } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Alert, Button, FormField, Pagination, Spinner } from '../../shared/ui';

export function ReceivablesPage() {
  const { locale } = useI18n();
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => parseInvoiceListState(params), [params]);
  const [search, setSearch] = useState(state.search);
  const valid = validInvoiceRanges(state);
  const query = useQuery({ ...receivableQueries.invoices(toInvoiceListQuery(state)), enabled: valid, placeholderData: keepPreviousData });
  const change = (patch: Partial<InvoiceListState>) => setParams(serializeInvoiceListState(updateInvoiceListState(state, patch)));

  useEffect(() => setSearch(state.search), [state.search]);
  useEffect(() => {
    if (search.trim() === state.search) return;
    const timer = window.setTimeout(() => change({ search: search.trim() }), 300);
    return () => window.clearTimeout(timer);
  }, [search, state.search]);
  useEffect(() => {
    const total = query.data?.totalPages;
    if (total !== undefined && state.page > 0 && state.page >= total) change({ page: Math.max(0, total - 1) });
  }, [query.data?.totalPages, state.page]);

  return <div className="customers-page receivables-page">
    <header className="customers-page__header"><div><p className="eyebrow">Receivables</p><h1>Задолженности</h1><p>{query.data ? `${query.data.totalElements} счетов · business date ${formatLocalDate(query.data.businessDate, locale)}` : 'Загрузка реестра счетов'}</p></div></header>
    <section className="customer-filters">
      <div className="customer-filters__quick">
        <FormField label="Поиск"><input type="search" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Номер или external ID" /></FormField>
        <FormField label="Статус"><select value={state.paymentStatus} onChange={(e) => change({ paymentStatus: e.target.value as '' | PaymentStatus })}><option value="">Все</option><option value="OPEN">OPEN</option><option value="PARTIALLY_PAID">PARTIALLY_PAID</option><option value="PAID">PAID</option><option value="CANCELLED">CANCELLED</option></select></FormField>
        <FormField label="Валюта"><input value={state.currency} maxLength={3} onChange={(e) => change({ currency: e.target.value.toUpperCase() })} /></FormField>
        <Button variant="secondary" onClick={() => { setSearch(''); setParams(serializeInvoiceListState(defaultInvoiceListState)); }}>Сбросить</Button>
      </div>
      <fieldset className="customer-filters__grid"><legend>Фильтры задолженности</legend>
        <FormField label="Срок с"><input type="date" value={state.dueFrom} onChange={(e) => change({ dueFrom: e.target.value })} /></FormField>
        <FormField label="Срок по"><input type="date" value={state.dueTo} onChange={(e) => change({ dueTo: e.target.value })} /></FormField>
        <FormField label="Просрочка"><select value={state.overdue} onChange={(e) => change({ overdue: e.target.value as '' | 'true' | 'false' })}><option value="">Все</option><option value="true">Только просроченные</option><option value="false">Не просроченные</option></select></FormField>
        <FormField label="Остаток от"><input inputMode="decimal" value={state.outstandingMin} onChange={(e) => change({ outstandingMin: e.target.value })} /></FormField>
        <FormField label="Остаток до"><input inputMode="decimal" value={state.outstandingMax} onChange={(e) => change({ outstandingMax: e.target.value })} /></FormField>
        <FormField label="Строк"><select value={state.size} onChange={(e) => change({ size: Number(e.target.value) as InvoiceListState['size'] })}>{[20,50,100].map((n) => <option key={n}>{n}</option>)}</select></FormField>
        <FormField label="Сортировка"><select value={state.sort} onChange={(e) => change({ sort: e.target.value })}><option value="createdAt,desc">Сначала новые</option><option value="dueDate,asc">Ближайший срок</option><option value="dueDate,desc">Поздний срок</option><option value="outstandingAmount,desc">Больший остаток</option><option value="outstandingAmount,asc">Меньший остаток</option></select></FormField>
      </fieldset>
    </section>
    {!valid ? <Alert variant="danger" title="Некорректный диапазон">Начальная дата не может быть позже конечной.</Alert> : null}
    {valid && query.isLoading ? <div className="customers-page__loading"><Spinner label="Загружаем задолженности…" /></div> : null}
    {valid && query.error ? <ProblemDetailPanel error={query.error} onRetry={() => void query.refetch()} /> : null}
    {query.data ? <><InvoiceTable rows={query.data.items} /><Pagination page={query.data.page} totalPages={query.data.totalPages} onPageChange={(page) => change({ page })} label="Страницы задолженности" previousLabel="Назад" nextLabel="Далее" /></> : null}
  </div>;
}
