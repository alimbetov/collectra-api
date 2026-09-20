import type {
  ContractCreateCommand, ContractDetailDto, ContractUpdateCommand,
} from '../../../entities/contract/model/contract.types';

export interface ContractForm {
  customerId: string;
  externalId: string;
  contractNumber: string;
  validFrom: string;
  validTo: string;
  renewalDate: string;
  customFields: string;
  version: number;
}

export type ContractFormErrors = Partial<Record<keyof ContractForm, string>>;
const nullable = (value: string) => value.trim() || null;

export function contractToForm(contract?: ContractDetailDto, customerId = ''): ContractForm {
  return {
    customerId: contract?.customerId ?? customerId,
    externalId: contract?.externalId ?? '',
    contractNumber: contract?.contractNumber ?? '',
    validFrom: contract?.validFrom ?? '',
    validTo: contract?.validTo ?? '',
    renewalDate: contract?.renewalDate ?? '',
    customFields: contract?.customFields == null ? '' : JSON.stringify(contract.customFields, null, 2),
    version: contract?.version ?? 0,
  };
}

export function validateContractForm(form: ContractForm, editing: boolean): ContractFormErrors {
  const errors: ContractFormErrors = {};
  if (!editing && !form.customerId) errors.customerId = 'required';
  if (!editing && !form.externalId.trim()) errors.externalId = 'required';
  if (form.externalId.trim().length > 120) errors.externalId = 'tooLong';
  if (!form.contractNumber.trim()) errors.contractNumber = 'required';
  if (form.contractNumber.trim().length > 160) errors.contractNumber = 'tooLong';
  if (!form.validFrom) errors.validFrom = 'required';
  if (form.validFrom && form.validTo && form.validTo < form.validFrom) errors.validTo = 'invalidRange';
  if (form.customFields.trim()) {
    try { JSON.parse(form.customFields); } catch { errors.customFields = 'invalidJson'; }
  }
  return errors;
}

export function contractCreateCommand(form: ContractForm): ContractCreateCommand {
  return {
    customerId: form.customerId,
    externalId: form.externalId.trim(),
    contractNumber: form.contractNumber.trim(),
    validFrom: form.validFrom,
    validTo: nullable(form.validTo),
    renewalDate: nullable(form.renewalDate),
    customFields: form.customFields.trim() ? JSON.parse(form.customFields) : null,
  };
}

export function contractUpdateCommand(form: ContractForm): ContractUpdateCommand {
  return {
    contractNumber: form.contractNumber.trim(),
    validFrom: form.validFrom,
    validTo: nullable(form.validTo),
    renewalDate: nullable(form.renewalDate),
    customFields: form.customFields.trim() ? JSON.parse(form.customFields) : null,
    version: form.version,
  };
}
