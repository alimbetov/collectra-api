import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { customerKeys } from '../../../entities/customer/api/customer.queries';
import { refreshContactMutation } from './contact-mutations';

describe('contact mutation cache contract', () => {
  it('refreshes the full affected collection and customer lists only', async () => {
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue();
    await refreshContactMutation(client, 'customer', 'email');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: customerKeys.emails('customer'), refetchType: 'active' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: customerKeys.lists(), refetchType: 'active' });
    expect(invalidate).not.toHaveBeenCalledWith(expect.objectContaining({ queryKey: customerKeys.phones('customer') }));
  });
});
