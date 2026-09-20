import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { customerQueries } from '../../../entities/customer/api/customer.queries';
import type { CustomerDetailDto, SegmentSummaryDto } from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, Dialog, FormField, Spinner } from '../../../shared/ui';
import { membershipDelta, type MembershipDelta } from '../model/segment-membership';

interface Props {
  open: boolean;
  customer: CustomerDetailDto;
  pending: boolean;
  error: unknown;
  onSave: (delta: MembershipDelta) => void;
  onReload: () => Promise<unknown>;
  onClose: () => void;
  onDirtyChange: (dirty: boolean) => void;
}

function useDebounced(value: string): string {
  const [result, setResult] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setResult(value), 300);
    return () => window.clearTimeout(timer);
  }, [value]);
  return result;
}

export function CustomerSegmentsDialog({ open, customer, pending, error, onSave, onReload, onClose, onDirtyChange }: Props) {
  const { t } = useI18n();
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounced(search);
  const [selected, setSelected] = useState<Set<string>>(() => new Set(customer.segmentIds));
  const [confirmClose, setConfirmClose] = useState(false);
  const membershipKey = customer.segmentIds.join(',');
  const options = useQuery({ ...customerQueries.segmentOptions(debouncedSearch), enabled: open });
  const delta = membershipDelta(customer.segmentIds, selected);
  const dirty = delta.additions.length > 0 || delta.removals.length > 0;
  const code = error instanceof ApiError ? error.problem?.code : null;

  useEffect(() => { if (open) setSelected(new Set(customer.segmentIds)); }, [membershipKey, open]);
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

  const rows = useMemo(() => {
    const values = new Map<string, SegmentSummaryDto>();
    customer.segments.forEach((segment) => values.set(segment.id, segment));
    options.data?.items.forEach((segment) => values.set(segment.id, segment));
    return [...values.values()].sort((a, b) => a.name.localeCompare(b.name));
  }, [customer.segments, options.data?.items]);
  const toggle = (id: string, checked: boolean) => setSelected((current) => {
    const next = new Set(current);
    if (checked) next.add(id); else next.delete(id);
    return next;
  });
  const requestClose = () => dirty ? setConfirmClose(true) : onClose();

  return <>
    <Dialog
      open={open}
      title={t('segments.membershipTitle')}
      closeLabel={t('customerEdit.close')}
      closeDisabled={pending}
      onClose={requestClose}
      actions={<>
        <Button variant="secondary" disabled={pending} onClick={requestClose}>{t('customerEdit.cancel')}</Button>
        <Button loading={pending} disabled={!dirty} onClick={() => onSave(delta)}>{t('customerEdit.save')}</Button>
      </>}
    >
      <div className="customer-edit-form">
        {code === 'INACTIVE_SEGMENT' ? <Alert variant="warning">{t('segments.inactiveConflict')}</Alert> : null}
        {error && code !== 'INACTIVE_SEGMENT' ? <Alert variant="danger">{t('segments.membershipFailed')}</Alert> : null}
        <FormField label={t('segments.search')}>
          <input type="search" value={search} onChange={(event) => setSearch(event.target.value)} />
        </FormField>
        {options.isFetching ? <Spinner size="small" label={t('customers.filters.loading')} /> : null}
        <fieldset className="segment-membership-list">
          <legend>{t('segments.available')}</legend>
          {rows.map((segment) => {
            const assigned = customer.segmentIds.includes(segment.id);
            const inactive = segment.active === false;
            return <label key={segment.id}>
              <input
                type="checkbox"
                checked={selected.has(segment.id)}
                disabled={inactive && !assigned}
                onChange={(event) => toggle(segment.id, event.target.checked)}
              />
              <span><strong>{segment.name}</strong> ({segment.code}){inactive ? ` — ${t('segments.inactive')}` : ''}</span>
            </label>;
          })}
          {!rows.length && !options.isFetching ? <p>{t('segments.noOptions')}</p> : null}
        </fieldset>
        {dirty ? <p aria-live="polite">{t('segments.delta')
          .replace('{add}', String(delta.additions.length))
          .replace('{remove}', String(delta.removals.length))}</p> : null}
        {error ? <Button variant="secondary" onClick={() => void onReload()}>{t('customerEdit.reload')}</Button> : null}
      </div>
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
