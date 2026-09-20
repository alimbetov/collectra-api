import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, Navigate, useBlocker, useParams } from 'react-router-dom';
import { transitionContract, updateContract } from '../../entities/contract/api/contract.api';
import { contractQueries } from '../../entities/contract/api/contract.queries';
import type { ContractLifecycleAction, ContractUpdateCommand } from '../../entities/contract/model/contract.types';
import { isCustomerId } from '../../features/customer-detail/model/customer-detail';
import { allowedContractActions } from '../../features/contracts/model/contract-lifecycle';
import { applyContractMutation } from '../../features/contracts/model/contract-mutations';
import { ContractFormDialog } from '../../features/contracts/ui/ContractFormDialog';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { formatInstant, formatLocalDate } from '../../shared/i18n/formatters';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button, ConfirmDialog, EmptyState, Spinner, StatusBadge, useToast } from '../../shared/ui';

export function ContractDetailPage() {
  const { contractId } = useParams();
  const validId = isCustomerId(contractId);
  const id = validId ? contractId : '';
  const { t, locale, timeZone } = useI18n();
  const client = useQueryClient();
  const { showToast } = useToast();
  const [editOpen, setEditOpen] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [action, setAction] = useState<ContractLifecycleAction | null>(null);
  const blocker = useBlocker(dirty);
  const detail = useQuery({ ...contractQueries.detail(id), enabled: validId });
  const updateMutation = useMutation({
    mutationFn: (command: ContractUpdateCommand) => updateContract(id, command),
    onSuccess: async (contract) => { await applyContractMutation(client, contract); setDirty(false); setEditOpen(false); showToast({ title: t('contracts.updated'), tone: 'success' }); },
  });
  const lifecycle = useMutation({
    mutationFn: (intent: ContractLifecycleAction) => transitionContract(id, intent, detail.data?.version ?? -1),
    onSuccess: async (contract) => { await applyContractMutation(client, contract, true); setAction(null); showToast({ title: t('contracts.lifecycleSaved'), tone: 'success' }); },
    onError: () => setAction(null),
  });
  if (detail.error instanceof ApiError && detail.error.status === 403) return <Navigate to="/forbidden" replace />;
  if (!validId || (detail.error instanceof ApiError && detail.error.status === 404)) return <div className="customer-detail customer-detail__not-found"><EmptyState title={t('contracts.notFound')} description={t('contracts.notFoundDescription')} /><Link to="/contracts">{t('contracts.back')}</Link></div>;
  if (detail.isLoading) return <div className="customer-detail__loading"><Spinner label={t('contracts.loadingDetail')} /></div>;
  if (detail.error) return <ProblemDetailPanel error={detail.error} onRetry={() => void detail.refetch()} />;
  const contract = detail.data;
  if (!contract) return null;
  const lifecycleCode = lifecycle.error instanceof ApiError ? lifecycle.error.problem?.code : null;
  return <div className="customer-detail contract-detail">
    <header className="customer-detail__header"><div><Link className="customer-detail__back" to="/contracts">← {t('contracts.back')}</Link><p className="eyebrow">{contract.externalId}</p><h1>{contract.contractNumber}</h1></div><div className="customer-detail__classification"><StatusBadge tone={contract.status === 'ACTIVE' ? 'success' : contract.status === 'SUSPENDED' ? 'warning' : contract.status === 'CANCELLED' ? 'danger' : 'neutral'}>{t(`contracts.status.${contract.status}`)}</StatusBadge><Button variant="secondary" onClick={() => { updateMutation.reset(); setEditOpen(true); }}>{t('customerEdit.action')}</Button>{allowedContractActions[contract.status].map((value) => <Button key={value} variant={value === 'cancel' ? 'danger' : 'secondary'} onClick={() => { lifecycle.reset(); setAction(value); }}>{t(`contracts.action.${value}` as const)}</Button>)}</div></header>
    {lifecycle.error ? <ProblemDetailPanel error={lifecycle.error} onRetry={lifecycleCode === 'VERSION_CONFLICT' || lifecycleCode === 'INVALID_STATE_TRANSITION' ? () => void detail.refetch().then(() => lifecycle.reset()) : undefined} /> : null}
    <div className="customer-detail-grid">
      <section className="customer-detail-card"><h2>{t('contracts.customer')}</h2><div className="customer-name-cell"><Link to={`/customers/${contract.customerId}`}>{contract.customerDisplayName ?? contract.customerExternalId}</Link><span>{contract.customerExternalId}</span></div></section>
      <section className="customer-detail-card"><h2>{t('contracts.validity')}</h2><dl className="customer-detail-fields"><div><dt>{t('contracts.validFrom')}</dt><dd>{formatLocalDate(contract.validFrom, locale)}</dd></div><div><dt>{t('contracts.validTo')}</dt><dd>{contract.validTo ? formatLocalDate(contract.validTo, locale) : t('contracts.openEnded')}</dd></div><div><dt>{t('contracts.renewalDate')}</dt><dd>{contract.renewalDate ? formatLocalDate(contract.renewalDate, locale) : '—'}</dd></div></dl></section>
      <section className="customer-detail-card"><h2>{t('contracts.audit')}</h2><dl className="customer-detail-fields"><div><dt>{t('customerDetail.created')}</dt><dd>{formatInstant(contract.createdAt, locale, timeZone)}</dd></div><div><dt>{t('customers.columns.updated')}</dt><dd>{formatInstant(contract.updatedAt, locale, timeZone)}</dd></div><div><dt>{t('contracts.version')}</dt><dd>{contract.version}</dd></div></dl></section>
      <section className="customer-detail-card"><h2>{t('contracts.customFields')}</h2>{contract.customFields == null ? <p>{t('customerDetail.noCustomFields')}</p> : <pre className="customer-custom-fields">{JSON.stringify(contract.customFields, null, 2)}</pre>}</section>
    </div>
    <ContractFormDialog open={editOpen} contract={contract} pending={updateMutation.isPending} error={updateMutation.error} onCreate={() => undefined} onUpdate={(command) => updateMutation.mutate(command)} onReload={async () => { updateMutation.reset(); return detail.refetch(); }} onClose={() => { updateMutation.reset(); setEditOpen(false); }} onDirtyChange={setDirty} />
    <ConfirmDialog open={Boolean(action)} title={t(action ? `contracts.confirm.${action}.title` as const : 'contracts.lifecycleTitle')} confirmLabel={t(action ? `contracts.action.${action}` as const : 'contracts.lifecycleConfirm')} cancelLabel={t('customerEdit.cancel')} closeLabel={t('customerEdit.close')} pending={lifecycle.isPending} destructive={action === 'cancel' || action === 'close'} onConfirm={() => { if (action) lifecycle.mutate(action); }} onCancel={() => setAction(null)}>{t(action ? `contracts.confirm.${action}.description` as const : 'contracts.lifecycleDescription')}</ConfirmDialog>
    <ConfirmDialog open={blocker.state === 'blocked'} title={t('customerEdit.unsavedTitle')} confirmLabel={t('customerEdit.discard')} cancelLabel={t('customerEdit.continue')} closeLabel={t('customerEdit.close')} destructive onConfirm={() => { setDirty(false); blocker.proceed?.(); }} onCancel={() => blocker.reset?.()}>{t('customerEdit.unsavedDescription')}</ConfirmDialog>
  </div>;
}
