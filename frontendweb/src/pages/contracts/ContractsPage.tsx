import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Navigate, useBlocker, useSearchParams } from 'react-router-dom';
import { createContract } from '../../entities/contract/api/contract.api';
import { contractQueries } from '../../entities/contract/api/contract.queries';
import { contractStatuses, type ContractCreateCommand, type ContractStatus } from '../../entities/contract/model/contract.types';
import {
  defaultContractListState, hasValidContractRanges, parseContractListState,
  serializeContractListState, toContractListQuery, updateContractListState,
  type ContractListState,
} from '../../features/contracts/model/contract-list-filters';
import { applyContractCreate } from '../../features/contracts/model/contract-mutations';
import { ContractFormDialog } from '../../features/contracts/ui/ContractFormDialog';
import { ContractTable } from '../../features/contracts/ui/ContractTable';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, FormField, Pagination, Spinner, useToast } from '../../shared/ui';

export function ContractsPage() {
  const { t } = useI18n();
  const { showToast } = useToast();
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const state = useMemo(() => parseContractListState(params), [params]);
  const [search, setSearch] = useState(state.search);
  const [createOpen, setCreateOpen] = useState(false);
  const [dirty, setDirty] = useState(false);
  const blocker = useBlocker(dirty);
  const validRanges = hasValidContractRanges(state);
  const contracts = useQuery({
    ...contractQueries.list(toContractListQuery(state)), enabled: validRanges, placeholderData: keepPreviousData,
  });
  const navigate = useCallback((next: ContractListState, replace = false) => setParams(serializeContractListState(next), { replace }), [setParams]);
  const change = (patch: Partial<ContractListState>) => navigate(updateContractListState(state, patch));

  useEffect(() => setSearch(state.search), [state.search]);
  useEffect(() => {
    if (search.trim() === state.search) return;
    const timer = window.setTimeout(() => navigate(updateContractListState(state, { search: search.trim() }), true), 300);
    return () => window.clearTimeout(timer);
  }, [navigate, search, state]);
  useEffect(() => {
    const total = contracts.data?.totalPages;
    if (total !== undefined && state.page > 0 && state.page >= total) change({ page: Math.max(0, total - 1) });
  }, [contracts.data?.totalPages, state.page]);

  const createMutation = useMutation({
    mutationFn: (command: ContractCreateCommand) => createContract(command),
    onSuccess: async () => {
      await applyContractCreate(client);
      setDirty(false); setCreateOpen(false);
      showToast({ title: t('contracts.created'), tone: 'success' });
    },
  });

  if (contracts.error instanceof ApiError && contracts.error.status === 403) return <Navigate to="/forbidden" replace />;
  return <div className="customers-page contracts-page">
    <header className="customers-page__header"><div><p className="eyebrow">{t('contracts.eyebrow')}</p><h1>{t('contracts.title')}</h1><p>{contracts.data ? t('contracts.total').replace('{count}', String(contracts.data.totalElements)) : t('contracts.totalPending')}</p></div><Button onClick={() => { createMutation.reset(); setCreateOpen(true); }}>{t('contracts.create')}</Button></header>
    <section className="customer-filters">
      <div className="customer-filters__quick">
        <FormField label={t('contracts.search')}><input type="search" value={search} onChange={(event) => setSearch(event.target.value)} /></FormField>
        <FormField label={t('contracts.status')}><select value={state.status} onChange={(event) => change({ status: event.target.value as '' | ContractStatus })}><option value="">{t('customers.filters.any')}</option>{contractStatuses.map((status) => <option key={status} value={status}>{t(`contracts.status.${status}`)}</option>)}</select></FormField>
        <Button variant="secondary" onClick={() => { setSearch(''); navigate(defaultContractListState); }}>{t('customers.filters.clear')}</Button>
      </div>
      <fieldset className="customer-filters__grid"><legend>{t('customers.filters.legend')}</legend>
        <FormField label={t('contracts.externalId')}><input value={state.externalId} onChange={(event) => change({ externalId: event.target.value })} /></FormField>
        <FormField label={t('contracts.validFrom')}><input type="date" value={state.validFrom} onChange={(event) => change({ validFrom: event.target.value })} /></FormField>
        <FormField label={t('contracts.validTo')}><input type="date" value={state.validTo} onChange={(event) => change({ validTo: event.target.value })} /></FormField>
        <FormField label={t('contracts.createdFrom')}><input type="date" value={state.createdFrom} onChange={(event) => change({ createdFrom: event.target.value })} /></FormField>
        <FormField label={t('contracts.createdTo')}><input type="date" value={state.createdTo} onChange={(event) => change({ createdTo: event.target.value })} /></FormField>
        <FormField label={t('customers.filters.size')}><select value={state.size} onChange={(event) => change({ size: Number(event.target.value) as ContractListState['size'] })}>{[20, 50, 100].map((size) => <option key={size}>{size}</option>)}</select></FormField>
        <FormField label={t('customers.filters.sort')}><select value={state.sort} onChange={(event) => change({ sort: event.target.value as ContractListState['sort'] })}>
          <option value="createdAt,desc">{t('customers.sort.createdDesc')}</option><option value="createdAt,asc">{t('customers.sort.createdAsc')}</option><option value="updatedAt,desc">{t('customers.sort.updatedDesc')}</option><option value="contractNumber,asc">{t('contracts.sortNumber')}</option><option value="contractNumber,desc">{t('contracts.sortNumberDesc')}</option><option value="validFrom,desc">{t('contracts.sortValidFromDesc')}</option><option value="validFrom,asc">{t('contracts.sortValidFromAsc')}</option>
        </select></FormField>
      </fieldset>
    </section>
    {!validRanges ? <Alert variant="danger" title={t('contracts.invalidRange')}>{t('contracts.invalidRangeDescription')}</Alert> : null}
    {validRanges && contracts.isLoading ? <div className="customers-page__loading"><Spinner label={t('contracts.loading')} /></div> : null}
    {validRanges && contracts.error ? <ProblemDetailPanel error={contracts.error} onRetry={() => void contracts.refetch()} /> : null}
    {contracts.data ? <><ContractTable rows={contracts.data.items} /><Pagination page={contracts.data.page} totalPages={contracts.data.totalPages} onPageChange={(page) => change({ page })} label={t('contracts.pagination')} previousLabel={t('customers.pagination.previous')} nextLabel={t('customers.pagination.next')} /></> : null}
    <ContractFormDialog open={createOpen} pending={createMutation.isPending} error={createMutation.error} onCreate={(command) => createMutation.mutate(command)} onUpdate={() => undefined} onReload={async () => { createMutation.reset(); }} onClose={() => { createMutation.reset(); setCreateOpen(false); }} onDirtyChange={setDirty} />
    <ConfirmDialog open={blocker.state === 'blocked'} title={t('customerEdit.unsavedTitle')} confirmLabel={t('customerEdit.discard')} cancelLabel={t('customerEdit.continue')} closeLabel={t('customerEdit.close')} destructive onConfirm={() => { setDirty(false); blocker.proceed?.(); }} onCancel={() => blocker.reset?.()}>{t('customerEdit.unsavedDescription')}</ConfirmDialog>
  </div>;
}
