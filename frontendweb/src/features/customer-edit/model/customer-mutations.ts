import type { QueryClient } from '@tanstack/react-query';
import { customerKeys } from '../../../entities/customer/api/customer.queries';
import type { CustomerDetailDto } from '../../../entities/customer/model/customer.types';
import { dashboardKeys } from '../../../entities/dashboard/api/dashboard.queries';

export async function applyCustomerMutation(
  queryClient: QueryClient,
  customer: CustomerDetailDto,
): Promise<void> {
  queryClient.setQueryData(customerKeys.detail(customer.id), customer);
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: customerKeys.lists(), refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: dashboardKeys.all, refetchType: 'active' }),
  ]);
}
