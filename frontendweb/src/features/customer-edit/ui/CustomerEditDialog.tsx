import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { customerQueries } from '../../../entities/customer/api/customer.queries';
import type { CustomerDetailDto, CustomerUpdateCommand } from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, Dialog, FormField, Spinner } from '../../../shared/ui';
import {
  customerEditCommand,
  customerToEditForm,
  sameCustomerEdit,
  validateCustomerEdit,
  type CustomerEditErrors,
  type CustomerEditForm,
} from '../model/customer-edit';

interface CustomerEditDialogProps {
  open: boolean;
  customer: CustomerDetailDto;
  canReadUsers: boolean;
  pending: boolean;
  error: unknown;
  onSave: (command: CustomerUpdateCommand) => void;
  onReload: () => Promise<unknown>;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

function useDebouncedValue(value: string, delayMs: number): string {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [delayMs, value]);
  return debounced;
}

function code(error: unknown): string | null {
  return error instanceof ApiError ? error.problem?.code ?? null : null;
}

function serverErrors(error: unknown): CustomerEditErrors {
  if (!(error instanceof ApiError) || !error.problem?.errors) return {};
  const allowed = new Set<keyof CustomerEditForm>([
    'displayName', 'firstName', 'lastName', 'middleName', 'companyName',
    'managerUserId', 'preferredLocale', 'timezone', 'customFields', 'version',
  ]);
  return Object.fromEntries(Object.entries(error.problem.errors)
    .filter(([key]) => allowed.has(key as keyof CustomerEditForm))
    .map(([key, value]) => [key, Array.isArray(value) ? value.join(', ') : value]));
}

export function CustomerEditDialog({
  open, customer, canReadUsers, pending, error, onSave, onReload, onClose, onDirtyChange,
}: CustomerEditDialogProps) {
  const { t } = useI18n();
  const initial = useMemo(
    () => customerToEditForm(customer),
    [customer.id, customer.version],
  );
  const [form, setForm] = useState(initial);
  const [clientErrors, setClientErrors] = useState<CustomerEditErrors>({});
  const [managerSearch, setManagerSearch] = useState('');
  const [confirmClose, setConfirmClose] = useState(false);
  const debouncedManagerSearch = useDebouncedValue(managerSearch, 300);
  const managers = useQuery({
    ...customerQueries.managerOptions(debouncedManagerSearch),
    enabled: open && canReadUsers,
  });
  const dirty = !sameCustomerEdit(form, initial);
  const errors = { ...serverErrors(error), ...clientErrors };
  const conflict = code(error) === 'VERSION_CONFLICT';
  const invalidManager = code(error) === 'INVALID_MANAGER';

  useEffect(() => {
    if (open) {
      setForm(initial);
      setClientErrors({});
    }
  }, [initial, open]);

  useEffect(() => {
    if (!open || !dirty) return;
    const beforeUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
  }, [dirty, open]);

  useEffect(() => {
    onDirtyChange(open && dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange, open]);

  const set = (field: keyof CustomerEditForm, value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
    setClientErrors((current) => ({ ...current, [field]: undefined }));
  };
  const message = (value: string | undefined) => {
    if (!value) return undefined;
    if (value === 'required') return t('customerEdit.error.required');
    if (value === 'invalidJson') return t('customerEdit.error.invalidJson');
    if (value.startsWith('max')) return t('customerEdit.error.tooLong');
    return value;
  };
  const requestClose = () => dirty ? setConfirmClose(true) : onClose();
  const submit = () => {
    const validation = validateCustomerEdit(form);
    setClientErrors(validation);
    if (Object.keys(validation).length) return;
    onSave(customerEditCommand(form));
  };

  const currentManagerMissing = customer.managerUserId
    && !managers.data?.items.some((item) => item.userId === customer.managerUserId);

  return (
    <>
      <Dialog
        open={open}
        title={t('customerEdit.title')}
        closeLabel={t('customerEdit.close')}
        closeDisabled={pending}
        onClose={requestClose}
        actions={<>
          <Button type="button" variant="secondary" disabled={pending} onClick={requestClose}>{t('customerEdit.cancel')}</Button>
          <Button type="button" loading={pending} onClick={submit}>{t('customerEdit.save')}</Button>
        </>}
      >
        <form className="customer-edit-form" onSubmit={(event) => { event.preventDefault(); submit(); }}>
          {conflict ? <Alert variant="warning" title={t('customerEdit.conflictTitle')}>
            <p>{t('customerEdit.conflictDescription')}</p>
            <Button type="button" variant="secondary" onClick={() => void onReload()}>{t('customerEdit.reload')}</Button>
          </Alert> : null}
          {invalidManager ? <Alert variant="warning">{t('customerEdit.invalidManager')}</Alert> : null}
          {error && !conflict && !invalidManager && !Object.keys(serverErrors(error)).length
            ? <Alert variant="danger">{t('customerEdit.failed')}</Alert> : null}
          <FormField label={t('customerEdit.displayName')} error={message(errors.displayName)} required>
            <input value={form.displayName} maxLength={300} onChange={(event) => set('displayName', event.target.value)} />
          </FormField>
          <div className="customer-edit-form__grid">
            <FormField label={t('customerDetail.lastName')} error={message(errors.lastName)}>
              <input value={form.lastName} maxLength={120} onChange={(event) => set('lastName', event.target.value)} />
            </FormField>
            <FormField label={t('customerDetail.firstName')} error={message(errors.firstName)}>
              <input value={form.firstName} maxLength={120} onChange={(event) => set('firstName', event.target.value)} />
            </FormField>
            <FormField label={t('customerDetail.middleName')} error={message(errors.middleName)}>
              <input value={form.middleName} maxLength={120} onChange={(event) => set('middleName', event.target.value)} />
            </FormField>
            <FormField label={t('customerDetail.companyName')} error={message(errors.companyName)}>
              <input value={form.companyName} maxLength={300} onChange={(event) => set('companyName', event.target.value)} />
            </FormField>
          </div>
          {canReadUsers ? <div className="customer-edit-manager">
            <FormField label={t('customerEdit.managerSearch')}>
              <input value={managerSearch} onChange={(event) => setManagerSearch(event.target.value)} />
            </FormField>
            <FormField label={t('customers.columns.manager')} error={message(errors.managerUserId)}>
              <select value={form.managerUserId} onChange={(event) => set('managerUserId', event.target.value)}>
                <option value="">{t('customerEdit.noManager')}</option>
                {currentManagerMissing ? <option value={customer.managerUserId ?? ''}>{customer.managerDisplayName ?? customer.managerUserId}</option> : null}
                {managers.data?.items.map((manager) => <option key={manager.userId} value={manager.userId}>{manager.label}</option>)}
              </select>
            </FormField>
            {managers.isFetching ? <Spinner size="small" label={t('customers.filters.loading')} /> : null}
          </div> : <FormField label={t('customers.columns.manager')} hint={t('customerEdit.managerReadonly')}>
            <input value={customer.managerDisplayName ?? t('customers.missing')} readOnly />
          </FormField>}
          <div className="customer-edit-form__grid">
            <FormField label={t('customerDetail.locale')} error={message(errors.preferredLocale)}>
              <input value={form.preferredLocale} maxLength={35} onChange={(event) => set('preferredLocale', event.target.value)} />
            </FormField>
            <FormField label={t('customerDetail.timezone')} error={message(errors.timezone)}>
              <input value={form.timezone} maxLength={60} onChange={(event) => set('timezone', event.target.value)} />
            </FormField>
          </div>
          <FormField label={t('customerDetail.customFields')} error={message(errors.customFields)} hint={t('customerEdit.customFieldsHint')}>
            <textarea rows={8} value={form.customFields} onChange={(event) => set('customFields', event.target.value)} />
          </FormField>
        </form>
      </Dialog>
      <ConfirmDialog
        open={confirmClose}
        title={t('customerEdit.unsavedTitle')}
        confirmLabel={t('customerEdit.discard')}
        cancelLabel={t('customerEdit.continue')}
        closeLabel={t('customerEdit.close')}
        destructive
        onConfirm={() => { setConfirmClose(false); onClose(); }}
        onCancel={() => setConfirmClose(false)}
      >{t('customerEdit.unsavedDescription')}</ConfirmDialog>
    </>
  );
}
