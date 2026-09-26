import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { createContract } from '../../../entities/contract/api/contract.api';
import { contractQueries } from '../../../entities/contract/api/contract.queries';
import { contractStatuses, type ContractCreateCommand, type ContractStatus } from '../../../entities/contract/model/contract.types';
import type { CustomerDetailDto } from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { ProblemDetailPanel } from '../../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Button, EmptyState, Pagination, Spinner, useToast } from '../../../shared/ui';
import { applyContractCreate } from '../model/contract-mutations';
import { useAuth } from '../../../features/auth/model/auth-context';
import { ContractFormDialog } from './ContractFormDialog';
import { ContractTable } from './ContractTable';

export function CustomerContractsPanel({ customer, onDirtyChange }: { customer: CustomerDetailDto; onDirtyChange: (dirty: boolean) => void }) {
  const { t } = useI18n();
  const { showToast } = useToast();
  const { hasPermission } = useAuth();
  const canManageContract = hasPermission('CONTRACT_MANAGE');
  const client = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<'' | ContractStatus>('');
  const [createOpen, setCreateOpen] = useState(false);
  const contracts = useQuery({
    ...contractQueries.list({ customerId: customer.id, ...(status ? { status } : {}), page, size: 20, sort: 'createdAt,desc' }),
    placeholderData: keepPreviousData,
  });
  const createMutation = useMutation({
    mutationFn: (command: ContractCreateCommand) => createContract(command),
    onSuccess: async () => { await applyContractCreate(client); onDirtyChange(false); setCreateOpen(false); showToast({ title: t('contracts.created'), tone: 'success' }); },
  });
  if (contracts.error instanceof ApiError && contracts.error.status === 403) return <Navigate to="/forbidden" replace />;
  return <section className="customer-contracts">
    <div className="customer-contracts__toolbar"><label>{t('contracts.status')} <select value={status} onChange={(event) => { setStatus(event.target.value as '' | ContractStatus); setPage(0); }}><option value="">{t('customers.filters.any')}</option>{contractStatuses.map((value) => <option key={value} value={value}>{t(`contracts.status.${value}`)}</option>)}</select></label>{canManageContract ? <Button onClick={() => { createMutation.reset(); setCreateOpen(true); }}>{t('contracts.create')}</Button> : null}</div>
    {contracts.isLoading ? <Spinner label={t('contracts.loading')} /> : null}
    {contracts.error ? <ProblemDetailPanel error={contracts.error} onRetry={() => void contracts.refetch()} /> : null}
    {contracts.data?.items.length ? <ContractTable rows={contracts.data.items} showCustomer={false} /> : null}
    {contracts.data && !contracts.data.items.length ? <EmptyState title={t('contracts.empty')} description={t('contracts.customerEmptyDescription')} /> : null}
    {contracts.data ? <Pagination page={contracts.data.page} totalPages={contracts.data.totalPages} onPageChange={setPage} label={t('contracts.pagination')} previousLabel={t('customers.pagination.previous')} nextLabel={t('customers.pagination.next')} /> : null}
    {canManageContract ? <ContractFormDialog open={createOpen} customer={{ id: customer.id, label: `${customer.displayName} (${customer.externalId})` }} pending={createMutation.isPending} error={createMutation.error} onCreate={(command) => createMutation.mutate(command)} onUpdate={() => undefined} onReload={async () => { createMutation.reset(); }} onClose={() => { createMutation.reset(); setCreateOpen(false); }} onDirtyChange={onDirtyChange} /> : null}
  </section>;
}
