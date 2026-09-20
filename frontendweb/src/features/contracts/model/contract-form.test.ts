import { describe, expect, it } from 'vitest';
import { contractCreateCommand, contractToForm, contractUpdateCommand, validateContractForm } from './contract-form';

describe('contract form', () => {
  it('validates required values, date order and JSON', () => {
    const form = contractToForm();
    expect(validateContractForm(form, false)).toMatchObject({ customerId: 'required', externalId: 'required', contractNumber: 'required', validFrom: 'required' });
    form.customerId = 'customer'; form.externalId = ' EXT '; form.contractNumber = ' CN '; form.validFrom = '2026-05-01'; form.validTo = '2026-04-01'; form.customFields = '{';
    expect(validateContractForm(form, false)).toMatchObject({ validTo: 'invalidRange', customFields: 'invalidJson' });
  });

  it('normalizes create and preserves the captured update version', () => {
    const form = contractToForm(undefined, 'customer');
    Object.assign(form, { externalId: ' EXT ', contractNumber: ' CN ', validFrom: '2026-01-01', customFields: '{"x":1}' });
    expect(contractCreateCommand(form)).toMatchObject({ customerId: 'customer', externalId: 'EXT', contractNumber: 'CN', customFields: { x: 1 } });
    form.version = 7;
    expect(contractUpdateCommand(form).version).toBe(7);
  });
});
