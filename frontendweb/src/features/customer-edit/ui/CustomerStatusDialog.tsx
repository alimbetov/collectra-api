import { useEffect, useState } from 'react';
import type { CustomerDetailDto, CustomerStatus } from '../../../entities/customer/model/customer.types';
import { customerStatuses } from '../../../entities/customer/model/customer.types';
import { ApiError } from '../../../shared/api/http-client';
import { useI18n } from '../../../shared/i18n/i18n-context';
import { Alert, Button, Dialog, FormField } from '../../../shared/ui';

interface Props {
  open: boolean;
  customer: CustomerDetailDto;
  pending: boolean;
  error: unknown;
  onConfirm: (status: CustomerStatus, version: number) => void;
  onReload: () => Promise<unknown>;
  onClose: () => void;
}

export function CustomerStatusDialog({ open, customer, pending, error, onConfirm, onReload, onClose }: Props) {
  const { t } = useI18n();
  const [status, setStatus] = useState<CustomerStatus>(customer.status);
  useEffect(() => { if (open) setStatus(customer.status); }, [customer.status, open]);
  const conflict = error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT';
  return <Dialog
    open={open}
    title={t('customerStatus.title')}
    closeLabel={t('customerEdit.close')}
    closeDisabled={pending}
    onClose={onClose}
    actions={<>
      <Button variant="secondary" disabled={pending} onClick={onClose}>{t('customerEdit.cancel')}</Button>
      <Button loading={pending} disabled={status === customer.status} onClick={() => onConfirm(status, customer.version)}>{t('customerStatus.confirm')}</Button>
    </>}
  >
    {conflict ? <Alert variant="warning" title={t('customerEdit.conflictTitle')}>
      <p>{t('customerEdit.conflictDescription')}</p>
      <Button variant="secondary" onClick={() => void onReload()}>{t('customerEdit.reload')}</Button>
    </Alert> : null}
    {error && !conflict ? <Alert variant="danger">{t('customerEdit.failed')}</Alert> : null}
    <p>{t('customerStatus.current')}: <strong>{t(`customers.status.${customer.status}`)}</strong></p>
    <FormField label={t('customerStatus.target')}>
      <select value={status} onChange={(event) => setStatus(event.target.value as CustomerStatus)}>
        {customerStatuses.map((value) => <option key={value} value={value}>{t(`customers.status.${value}`)}</option>)}
      </select>
    </FormField>
  </Dialog>;
}
