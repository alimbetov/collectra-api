import { describe, expect, it } from 'vitest';
import { contactPatchCommand, contactToForm, emailCreateCommand, validateContact } from './customer-contact-edit';

describe('customer contact edit mapping', () => {
  it('uses the contact version and clears primary for inactive contacts', () => {
    const form = contactToForm({ id: 'e', email: 'a@b.kz', type: 'WORK', primary: true, verified: false, status: 'ACTIVE', version: 4 });
    form.status = 'INACTIVE';
    expect(contactPatchCommand(form)).toEqual({ type: 'WORK', primary: false, status: 'INACTIVE', version: 4 });
  });

  it('normalizes create values and validates email without changing server normalization', () => {
    const form = contactToForm();
    form.value = '  billing@example.kz  ';
    form.type = ' WORK ';
    expect(validateContact(form, 'email', false)).toEqual({});
    expect(emailCreateCommand(form)).toEqual({ email: 'billing@example.kz', type: 'WORK', primary: false });
    form.value = 'not-email';
    expect(validateContact(form, 'email', false)).toEqual({ value: 'invalidEmail' });
  });
});
