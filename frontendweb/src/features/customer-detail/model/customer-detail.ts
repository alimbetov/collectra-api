import type { CustomerEmailDto, CustomerPhoneDto } from '../../../entities/customer/model/customer.types';

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isCustomerId(value: string | undefined): value is string {
  return Boolean(value && uuidPattern.test(value));
}

function compareFlags(
  left: { primary: boolean; status: 'ACTIVE' | 'INACTIVE' },
  right: { primary: boolean; status: 'ACTIVE' | 'INACTIVE' },
): number {
  if (left.primary !== right.primary) return left.primary ? -1 : 1;
  if (left.status !== right.status) return left.status === 'ACTIVE' ? -1 : 1;
  return 0;
}

export function orderCustomerEmails(values: readonly CustomerEmailDto[]): CustomerEmailDto[] {
  return [...values].sort(
    (left, right) =>
      compareFlags(left, right) ||
      left.email.localeCompare(right.email, 'en', { sensitivity: 'base' }) ||
      left.id.localeCompare(right.id),
  );
}

export function orderCustomerPhones(values: readonly CustomerPhoneDto[]): CustomerPhoneDto[] {
  return [...values].sort(
    (left, right) =>
      compareFlags(left, right) ||
      left.normalizedPhone.localeCompare(right.normalizedPhone) ||
      left.id.localeCompare(right.id),
  );
}

export function customFieldsText(value: unknown | null): string | null {
  if (value === null || value === undefined) return null;
  try {
    return JSON.stringify(value, null, 2) ?? null;
  } catch {
    return null;
  }
}
