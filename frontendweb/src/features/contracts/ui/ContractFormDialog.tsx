import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { customerQueries } from '../../../entities/customer/api/customer.queries';
import type { ContractCreateCommand, ContractDetailDto, ContractUpdateCommand } from '../../../entities/contract/model/contract.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, Dialog, FormField, Spinner } from '../../../shared/ui';
import {
  contractCreateCommand, contractToForm, contractUpdateCommand, validateContractForm,
  type ContractForm, type ContractFormErrors,
} from '../model/contract-form';

interface CustomerPreset { id: string; label: string; }
interface Props {
  open: boolean;
  contract?: ContractDetailDto;
  customer?: CustomerPreset;
  pending: boolean;
  error: unknown;
  onCreate: (command: ContractCreateCommand) => void;
  onUpdate: (command: ContractUpdateCommand) => void;
  onReload: () => Promise<unknown>;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

const same = (left: ContractForm, right: ContractForm) => JSON.stringify(left) === JSON.stringify(right);

export function ContractFormDialog(props: Props) {
  const { open, contract, customer, pending, error, onCreate, onUpdate, onReload, onClose, onDirtyChange } = props;
  const { t } = useI18n();
  const editing = Boolean(contract);
  const initial = useMemo(() => contractToForm(contract, customer?.id), [contract?.id, contract?.version, customer?.id, open]);
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState<ContractFormErrors>({});
  const [customerSearch, setCustomerSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [confirmClose, setConfirmClose] = useState(false);
  const customers = useQuery({
    ...customerQueries.list({ search: debouncedSearch || undefined, page: 0, size: 20, sort: 'displayName,asc' }),
    enabled: open && !editing && !customer,
  });
  const dirty = !same(form, initial);
  const code = error instanceof ApiError ? error.problem?.code : null;

  useEffect(() => { if (open) { setForm(initial); setErrors({}); setCustomerSearch(''); setDebouncedSearch(''); } }, [initial, open]);
  useEffect(() => { const timer = window.setTimeout(() => setDebouncedSearch(customerSearch.trim()), 300); return () => window.clearTimeout(timer); }, [customerSearch]);
  useEffect(() => { onDirtyChange(open && dirty); return () => onDirtyChange(false); }, [dirty, onDirtyChange, open]);
  useEffect(() => {
    if (!open || !dirty) return;
    const handler = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty, open]);

  const patch = (value: Partial<ContractForm>) => setForm((current) => ({ ...current, ...value }));
  const submit = () => {
    const next = validateContractForm(form, editing);
    setErrors(next);
    if (Object.keys(next).length) return;
    if (editing) onUpdate(contractUpdateCommand(form)); else onCreate(contractCreateCommand(form));
  };
  const fieldError = (value?: string) => value === 'required' ? t('contracts.error.required')
    : value === 'tooLong' ? t('contracts.error.tooLong')
      : value === 'invalidRange' ? t('contracts.error.invalidRange')
        : value === 'invalidJson' ? t('contracts.error.invalidJson') : undefined;
  const requestClose = () => dirty ? setConfirmClose(true) : onClose();

  return <>
    <Dialog open={open} title={t(editing ? 'contracts.editTitle' : 'contracts.createTitle')} closeLabel={t('customerEdit.close')} closeDisabled={pending} onClose={requestClose} actions={<>
      <Button variant="secondary" disabled={pending} onClick={requestClose}>{t('customerEdit.cancel')}</Button>
      <Button loading={pending} onClick={submit}>{t('customerEdit.save')}</Button>
    </>}>
      <form className="customer-edit-form" onSubmit={(event) => { event.preventDefault(); submit(); }}>
        {code === 'VERSION_CONFLICT' ? <Alert variant="warning" title={t('contracts.conflictTitle')}><p>{t('contracts.conflictDescription')}</p><Button variant="secondary" onClick={() => void onReload()}>{t('customerEdit.reload')}</Button></Alert> : null}
        {code === 'DUPLICATE_EXTERNAL_ID' ? <Alert variant="warning">{t('contracts.duplicateExternalId')}</Alert> : null}
        {code === 'INVALID_RANGE' ? <Alert variant="warning">{t('contracts.invalidRange')}</Alert> : null}
        {error && !['VERSION_CONFLICT', 'DUPLICATE_EXTERNAL_ID', 'INVALID_RANGE'].includes(code ?? '') ? <Alert variant="danger">{t('contracts.saveFailed')}</Alert> : null}
        {!editing && !customer ? <>
          <FormField label={t('contracts.customerSearch')}><input value={customerSearch} onChange={(event) => { setCustomerSearch(event.target.value); patch({ customerId: '' }); }} /></FormField>
          {customers.isFetching ? <Spinner size="small" label={t('customers.filters.loading')} /> : null}
          <FormField label={t('contracts.customer')} error={fieldError(errors.customerId)} required><select value={form.customerId} onChange={(event) => patch({ customerId: event.target.value })}><option value="">{t('contracts.selectCustomer')}</option>{customers.data?.items.map((item) => <option key={item.id} value={item.id}>{item.displayName} ({item.externalId})</option>)}</select></FormField>
        </> : <FormField label={t('contracts.customer')}><input value={customer?.label ?? contract?.customerDisplayName ?? contract?.customerExternalId ?? ''} readOnly /></FormField>}
        <FormField label={t('contracts.externalId')} error={fieldError(errors.externalId)} required><input value={form.externalId} maxLength={120} readOnly={editing} onChange={(event) => patch({ externalId: event.target.value })} /></FormField>
        <FormField label={t('contracts.number')} error={fieldError(errors.contractNumber)} required><input value={form.contractNumber} maxLength={160} onChange={(event) => patch({ contractNumber: event.target.value })} /></FormField>
        <div className="contract-date-grid">
          <FormField label={t('contracts.validFrom')} error={fieldError(errors.validFrom)} required><input type="date" value={form.validFrom} onChange={(event) => patch({ validFrom: event.target.value })} /></FormField>
          <FormField label={t('contracts.validTo')} error={fieldError(errors.validTo)}><input type="date" value={form.validTo} onChange={(event) => patch({ validTo: event.target.value })} /></FormField>
          <FormField label={t('contracts.renewalDate')}><input type="date" value={form.renewalDate} onChange={(event) => patch({ renewalDate: event.target.value })} /></FormField>
        </div>
        <FormField label={t('contracts.customFields')} hint={t('customerEdit.customFieldsHint')} error={fieldError(errors.customFields)}><textarea rows={6} value={form.customFields} onChange={(event) => patch({ customFields: event.target.value })} /></FormField>
      </form>
    </Dialog>
    <ConfirmDialog open={confirmClose} title={t('customerEdit.unsavedTitle')} confirmLabel={t('customerEdit.discard')} cancelLabel={t('customerEdit.continue')} closeLabel={t('customerEdit.close')} destructive onConfirm={() => { setConfirmClose(false); onClose(); }} onCancel={() => setConfirmClose(false)}>{t('customerEdit.unsavedDescription')}</ConfirmDialog>
  </>;
}
