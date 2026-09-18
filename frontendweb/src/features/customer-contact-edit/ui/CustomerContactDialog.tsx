import { useEffect, useMemo, useState } from 'react';
import type { ContactPatchCommand, EmailCreateCommand, PhoneCreateCommand } from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, Dialog, FormField } from '../../../shared/ui';
import {
  contactPatchCommand,
  contactToForm,
  emailCreateCommand,
  phoneCreateCommand,
  validateContact,
  type ContactForm,
  type ContactFormErrors,
  type ContactKind,
  type CustomerContact,
} from '../model/customer-contact-edit';

interface Props {
  open: boolean;
  kind: ContactKind;
  contact?: CustomerContact;
  pending: boolean;
  error: unknown;
  onCreateEmail: (command: EmailCreateCommand) => void;
  onCreatePhone: (command: PhoneCreateCommand) => void;
  onPatch: (command: ContactPatchCommand) => void;
  onReload: () => Promise<unknown>;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

const knownTypes = ['WORK', 'HOME', 'MOBILE', 'OTHER'] as const;
const same = (a: ContactForm, b: ContactForm) => JSON.stringify(a) === JSON.stringify(b);
const problemCode = (error: unknown) => error instanceof ApiError ? error.problem?.code : null;

export function CustomerContactDialog({
  open, kind, contact, pending, error, onCreateEmail, onCreatePhone, onPatch,
  onReload, onClose, onDirtyChange,
}: Props) {
  const { t } = useI18n();
  const initial = useMemo(() => contactToForm(contact), [contact?.id, contact?.version, kind, open]);
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState<ContactFormErrors>({});
  const [confirmClose, setConfirmClose] = useState(false);
  const editing = Boolean(contact);
  const dirty = !same(form, initial);
  const code = problemCode(error);

  useEffect(() => { if (open) { setForm(initial); setErrors({}); } }, [initial, open]);
  useEffect(() => {
    onDirtyChange(open && dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange, open]);
  useEffect(() => {
    if (!open || !dirty) return;
    const beforeUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', beforeUnload);
    return () => window.removeEventListener('beforeunload', beforeUnload);
  }, [dirty, open]);

  const patch = (value: Partial<ContactForm>) => {
    setForm((current) => {
      const next = { ...current, ...value };
      return next.status === 'INACTIVE' ? { ...next, primary: false } : next;
    });
  };
  const requestClose = () => dirty ? setConfirmClose(true) : onClose();
  const submit = () => {
    const validation = validateContact(form, kind, editing);
    setErrors(validation);
    if (Object.keys(validation).length) return;
    if (editing) onPatch(contactPatchCommand(form));
    else if (kind === 'email') onCreateEmail(emailCreateCommand(form));
    else onCreatePhone(phoneCreateCommand(form));
  };
  const fieldError = (value?: string) => {
    if (value === 'required') return t('contactEdit.error.required');
    if (value === 'invalidEmail') return t('contactEdit.error.invalidEmail');
    if (value === 'tooLong') return t('contactEdit.error.tooLong');
    return value;
  };
  const unknownType = form.type && !knownTypes.includes(form.type.toUpperCase() as typeof knownTypes[number]);
  const title = editing
    ? t(kind === 'email' ? 'contactEdit.editEmail' : 'contactEdit.editPhone')
    : t(kind === 'email' ? 'contactEdit.addEmail' : 'contactEdit.addPhone');

  return <>
    <Dialog
      open={open}
      title={title}
      closeLabel={t('customerEdit.close')}
      closeDisabled={pending}
      onClose={requestClose}
      actions={<>
        <Button variant="secondary" disabled={pending} onClick={requestClose}>{t('customerEdit.cancel')}</Button>
        <Button loading={pending} onClick={submit}>{t('customerEdit.save')}</Button>
      </>}
    >
      <form className="customer-edit-form" onSubmit={(event) => { event.preventDefault(); submit(); }}>
        {code === 'VERSION_CONFLICT' ? <Alert variant="warning" title={t('customerEdit.conflictTitle')}>
          <p>{t('contactEdit.conflictDescription')}</p>
          <Button variant="secondary" onClick={() => void onReload()}>{t('customerEdit.reload')}</Button>
        </Alert> : null}
        {code === 'DUPLICATE_CONTACT' ? <Alert variant="warning">{t('contactEdit.duplicate')}</Alert> : null}
        {code === 'CONTACT_LIMIT_REACHED' ? <Alert variant="warning">{t(kind === 'email' ? 'contactEdit.emailLimit' : 'contactEdit.phoneLimit')}</Alert> : null}
        {code === 'INVALID_STATE_TRANSITION' ? <Alert variant="warning">{t('contactEdit.invalidState')}</Alert> : null}
        {error && !['VERSION_CONFLICT', 'DUPLICATE_CONTACT', 'CONTACT_LIMIT_REACHED', 'INVALID_STATE_TRANSITION'].includes(code ?? '')
          ? <Alert variant="danger">{t('contactEdit.failed')}</Alert> : null}
        <FormField
          label={t(kind === 'email' ? 'customers.columns.email' : 'customers.columns.phone')}
          error={fieldError(errors.value)}
          hint={editing ? t('contactEdit.valueImmutable') : undefined}
          required={!editing}
        >
          <input
            type={kind === 'email' ? 'email' : 'tel'}
            value={form.value}
            readOnly={editing}
            maxLength={kind === 'email' ? 320 : 40}
            onChange={(event) => { patch({ value: event.target.value }); setErrors((current) => ({ ...current, value: undefined })); }}
          />
        </FormField>
        <FormField label={t('customerDetail.contactType')} error={fieldError(errors.type)}>
          <select value={form.type} onChange={(event) => patch({ type: event.target.value })}>
            <option value="">{t('contactEdit.noType')}</option>
            {unknownType ? <option value={form.type}>{form.type}</option> : null}
            {knownTypes.map((type) => <option key={type} value={type}>{t(`customerDetail.contactType.${type}`)}</option>)}
          </select>
        </FormField>
        {editing ? <FormField label={t('customers.columns.status')}>
          <select value={form.status} onChange={(event) => patch({ status: event.target.value as ContactForm['status'] })}>
            <option value="ACTIVE">{t('customerDetail.contactStatus.ACTIVE')}</option>
            <option value="INACTIVE">{t('customerDetail.contactStatus.INACTIVE')}</option>
          </select>
        </FormField> : null}
        <label className="customer-edit-checkbox">
          <input type="checkbox" checked={form.primary} disabled={form.status === 'INACTIVE'} onChange={(event) => patch({ primary: event.target.checked })} />
          <span>{t('contactEdit.primary')}</span>
        </label>
        {editing ? <p className="ui-form-field__hint">{t('contactEdit.verifiedReadonly')}: {contact?.verified ? t('customerDetail.verified') : t('customerDetail.notVerified')}</p> : null}
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
  </>;
}
