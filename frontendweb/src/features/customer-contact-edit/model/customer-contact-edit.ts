import type {
  ContactPatchCommand,
  CustomerContactStatus,
  CustomerEmailDto,
  CustomerPhoneDto,
  EmailCreateCommand,
  PhoneCreateCommand,
} from '../../../entities/customer/model/customer.types';

export type ContactKind = 'email' | 'phone';
export type CustomerContact = CustomerEmailDto | CustomerPhoneDto;

export interface ContactForm {
  value: string;
  type: string;
  primary: boolean;
  status: CustomerContactStatus;
  version: number | null;
}

export interface ContactFormErrors {
  value?: string;
  type?: string;
}

export const contactValue = (contact: CustomerContact): string =>
  'email' in contact ? contact.email : contact.phone;

export function contactToForm(contact?: CustomerContact): ContactForm {
  return contact ? {
    value: contactValue(contact), type: contact.type ?? '', primary: contact.primary,
    status: contact.status, version: contact.version,
  } : { value: '', type: '', primary: false, status: 'ACTIVE', version: null };
}

export function validateContact(form: ContactForm, kind: ContactKind, editing: boolean): ContactFormErrors {
  const errors: ContactFormErrors = {};
  if (!editing && !form.value.trim()) errors.value = 'required';
  if (!editing && kind === 'email' && form.value.trim().length > 320) errors.value = 'tooLong';
  if (!editing && kind === 'phone' && form.value.trim().length > 40) errors.value = 'tooLong';
  if (!editing && kind === 'email' && form.value.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.trim())) {
    errors.value = 'invalidEmail';
  }
  if (form.type.trim().length > 20) errors.type = 'tooLong';
  return errors;
}

export function emailCreateCommand(form: ContactForm): EmailCreateCommand {
  return { email: form.value.trim(), type: form.type.trim() || null, primary: form.primary };
}

export function phoneCreateCommand(form: ContactForm): PhoneCreateCommand {
  return { phone: form.value.trim(), type: form.type.trim() || null, primary: form.primary };
}

export function contactPatchCommand(form: ContactForm): ContactPatchCommand {
  if (form.version == null) throw new Error('Contact version is required');
  return {
    type: form.type.trim() || null,
    primary: form.status === 'INACTIVE' ? false : form.primary,
    status: form.status,
    version: form.version,
  };
}
