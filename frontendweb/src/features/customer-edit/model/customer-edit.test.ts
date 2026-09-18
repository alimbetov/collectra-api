import { describe, expect, it } from 'vitest';
import type { CustomerDetailDto } from '../../../entities/customer/model/customer.types';
import { customerEditCommand, customerToEditForm, validateCustomerEdit } from './customer-edit';

const customer = {
  id: 'customer', externalId: 'EXT', customerType: 'INDIVIDUAL', displayName: ' Alice ',
  firstName: ' Alice ', lastName: null, middleName: null, companyName: null, status: 'ACTIVE',
  managerUserId: 'manager', managerDisplayName: 'Manager', preferredLocale: 'ru', timezone: 'Asia/Almaty',
  customFields: { priority: true }, segmentIds: [], segments: [], createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z', version: 7,
} satisfies CustomerDetailDto;

describe('customer edit mapping', () => {
  it('creates an independent form and a normalized full update command', () => {
    const form = customerToEditForm(customer);
    form.firstName = '  Alicia  ';
    form.lastName = '   ';
    form.customFields = '{"priority":false}';

    expect(customer.firstName).toBe(' Alice ');
    expect(customerEditCommand(form)).toEqual(expect.objectContaining({
      displayName: 'Alice', firstName: 'Alicia', lastName: null,
      managerUserId: 'manager', customFields: { priority: false }, version: 7,
    }));
  });

  it('validates required, bounded and JSON fields before transport', () => {
    const form = customerToEditForm(customer);
    form.displayName = ' ';
    form.firstName = 'x'.repeat(121);
    form.customFields = '{broken';
    expect(validateCustomerEdit(form)).toMatchObject({
      displayName: 'required', firstName: 'max120', customFields: 'invalidJson',
    });
  });
});
