import type {
  CustomerDetailDto,
  CustomerUpdateCommand,
} from '../../../entities/customer/model/customer.types';

export interface CustomerEditForm {
  displayName: string;
  firstName: string;
  lastName: string;
  middleName: string;
  companyName: string;
  managerUserId: string;
  preferredLocale: string;
  timezone: string;
  customFields: string;
  version: number;
}

export type CustomerEditErrors = Partial<Record<keyof CustomerEditForm, string>>;

const text = (value: string | null | undefined) => value ?? '';
const nullable = (value: string) => value.trim() || null;

export function customerToEditForm(customer: CustomerDetailDto): CustomerEditForm {
  return {
    displayName: customer.displayName,
    firstName: text(customer.firstName),
    lastName: text(customer.lastName),
    middleName: text(customer.middleName),
    companyName: text(customer.companyName),
    managerUserId: text(customer.managerUserId),
    preferredLocale: text(customer.preferredLocale),
    timezone: text(customer.timezone),
    customFields: customer.customFields == null ? '' : JSON.stringify(customer.customFields, null, 2),
    version: customer.version,
  };
}

export function validateCustomerEdit(form: CustomerEditForm): CustomerEditErrors {
  const errors: CustomerEditErrors = {};
  if (!form.displayName.trim()) errors.displayName = 'required';
  if (form.displayName.trim().length > 300) errors.displayName = 'max300';
  for (const field of ['firstName', 'lastName', 'middleName'] as const) {
    if (form[field].trim().length > 120) errors[field] = 'max120';
  }
  if (form.companyName.trim().length > 300) errors.companyName = 'max300';
  if (form.preferredLocale.trim().length > 35) errors.preferredLocale = 'max35';
  if (form.timezone.trim().length > 60) errors.timezone = 'max60';
  if (form.customFields.trim()) {
    try {
      JSON.parse(form.customFields);
    } catch {
      errors.customFields = 'invalidJson';
    }
  }
  return errors;
}

export function customerEditCommand(form: CustomerEditForm): CustomerUpdateCommand {
  return {
    displayName: form.displayName.trim(),
    firstName: nullable(form.firstName),
    lastName: nullable(form.lastName),
    middleName: nullable(form.middleName),
    companyName: nullable(form.companyName),
    managerUserId: nullable(form.managerUserId),
    preferredLocale: nullable(form.preferredLocale),
    timezone: nullable(form.timezone),
    customFields: form.customFields.trim() ? JSON.parse(form.customFields) : null,
    version: form.version,
  };
}

export function sameCustomerEdit(left: CustomerEditForm, right: CustomerEditForm): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}
