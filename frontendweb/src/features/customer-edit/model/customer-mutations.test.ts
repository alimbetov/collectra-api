import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { customerKeys } from '../../../entities/customer/api/customer.queries';
import { dashboardKeys } from '../../../entities/dashboard/api/dashboard.queries';
import type { CustomerDetailDto } from '../../../entities/customer/model/customer.types';
import { applyCustomerMutation } from './customer-mutations';

describe('customer mutation cache contract', () => {
  it('sets authoritative detail and invalidates only lists and dashboard', async () => {
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue();
    const customer = { id: 'customer', displayName: 'Updated', version: 8 } as CustomerDetailDto;

    await applyCustomerMutation(client, customer);

    expect(client.getQueryData(customerKeys.detail('customer'))).toBe(customer);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: customerKeys.lists(), refetchType: 'active' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: dashboardKeys.all, refetchType: 'active' });
    expect(invalidate).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: customerKeys.emails('customer') }));
  });
});
