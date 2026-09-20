import { useEffect, useMemo, useState } from 'react';
import type { SegmentCreateCommand, SegmentOptionDto, SegmentUpdateCommand } from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, Dialog, FormField } from '../../../shared/ui';
import {
  segmentCreateCommand, segmentToForm, segmentUpdateCommand, validateSegmentForm,
  type SegmentForm, type SegmentFormErrors,
} from '../model/segment-form';

interface Props {
  open: boolean;
  segment?: SegmentOptionDto;
  pending: boolean;
  error: unknown;
  onCreate: (command: SegmentCreateCommand) => void;
  onUpdate: (command: SegmentUpdateCommand) => void;
  onReload: () => Promise<unknown>;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

const same = (a: SegmentForm, b: SegmentForm) => JSON.stringify(a) === JSON.stringify(b);

export function SegmentFormDialog({ open, segment, pending, error, onCreate, onUpdate, onReload, onClose, onDirtyChange }: Props) {
  const { t } = useI18n();
  const initial = useMemo(() => segmentToForm(segment), [segment?.id, segment?.version, open]);
  const [form, setForm] = useState(initial);
  const [errors, setErrors] = useState<SegmentFormErrors>({});
  const [confirmClose, setConfirmClose] = useState(false);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const editing = Boolean(segment);
  const dirty = !same(form, initial);
  const code = error instanceof ApiError ? error.problem?.code : null;

  useEffect(() => { if (open) { setForm(initial); setErrors({}); } }, [initial, open]);
  useEffect(() => {
    onDirtyChange(open && dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange, open]);
  useEffect(() => {
    if (!open || !dirty) return;
    const handler = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty, open]);

  const patch = (value: Partial<SegmentForm>) => setForm((current) => ({ ...current, ...value }));
  const submitNow = () => editing ? onUpdate(segmentUpdateCommand(form)) : onCreate(segmentCreateCommand(form));
  const submit = () => {
    const nextErrors = validateSegmentForm(form, editing);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    if (editing && segment?.active && !form.active) setConfirmDeactivate(true);
    else submitNow();
  };
  const requestClose = () => dirty ? setConfirmClose(true) : onClose();
  const fieldError = (value?: string) => value === 'required'
    ? t('segments.error.required') : value === 'tooLong' ? t('segments.error.tooLong') : value;

  return <>
    <Dialog
      open={open}
      title={t(editing ? 'segments.editTitle' : 'segments.createTitle')}
      closeLabel={t('customerEdit.close')}
      closeDisabled={pending}
      onClose={requestClose}
      actions={<>
        <Button variant="secondary" disabled={pending} onClick={requestClose}>{t('customerEdit.cancel')}</Button>
        <Button loading={pending} onClick={submit}>{t('customerEdit.save')}</Button>
      </>}
    >
      <form className="customer-edit-form" onSubmit={(event) => { event.preventDefault(); submit(); }}>
        {code === 'VERSION_CONFLICT' ? <Alert variant="warning" title={t('segments.conflictTitle')}>
          <p>{t('segments.conflictDescription')}</p>
          <Button variant="secondary" onClick={() => void onReload()}>{t('customerEdit.reload')}</Button>
        </Alert> : null}
        {code === 'DUPLICATE_SEGMENT_CODE' ? <Alert variant="warning">{t('segments.duplicateCode')}</Alert> : null}
        {error && !['VERSION_CONFLICT', 'DUPLICATE_SEGMENT_CODE'].includes(code ?? '')
          ? <Alert variant="danger">{t('segments.saveFailed')}</Alert> : null}
        <FormField label={t('segments.code')} error={fieldError(errors.code)} required>
          <input value={form.code} maxLength={80} readOnly={editing} onChange={(event) => patch({ code: event.target.value })} />
        </FormField>
        <FormField label={t('segments.name')} error={fieldError(errors.name)} required>
          <input value={form.name} maxLength={200} onChange={(event) => patch({ name: event.target.value })} />
        </FormField>
        <FormField label={t('segments.description')} error={fieldError(errors.description)}>
          <textarea rows={5} value={form.description} maxLength={1000} onChange={(event) => patch({ description: event.target.value })} />
        </FormField>
        {editing ? <label className="customer-edit-checkbox">
          <input type="checkbox" checked={form.active} onChange={(event) => patch({ active: event.target.checked })} />
          <span>{t('segments.active')}</span>
        </label> : null}
      </form>
    </Dialog>
    <ConfirmDialog
      open={confirmDeactivate}
      title={t('segments.deactivateTitle')}
      confirmLabel={t('segments.deactivate')}
      cancelLabel={t('customerEdit.cancel')}
      closeLabel={t('customerEdit.close')}
      pending={pending}
      destructive
      onConfirm={() => { setConfirmDeactivate(false); submitNow(); }}
      onCancel={() => setConfirmDeactivate(false)}
    >{t('segments.deactivateDescription')}</ConfirmDialog>
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
