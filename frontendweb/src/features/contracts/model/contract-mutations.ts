import type { QueryClient } from '@tanstack/react-query';
import { contractKeys } from '../../../entities/contract/api/contract.queries';
import type { ContractDetailDto } from '../../../entities/contract/model/contract.types';
import { dashboardKeys } from '../../../entities/dashboard/api/dashboard.queries';

export async function applyContractMutation(
  queryClient: QueryClient,
  contract: ContractDetailDto,
  dashboardAffected = false,
): Promise<void> {
  queryClient.setQueryData(contractKeys.detail(contract.id), contract);
  const invalidations = [
    queryClient.invalidateQueries({ queryKey: contractKeys.lists(), refetchType: 'active' }),
  ];
  if (dashboardAffected) {
    invalidations.push(queryClient.invalidateQueries({ queryKey: dashboardKeys.summary(), refetchType: 'active' }));
  }
  await Promise.all(invalidations);
}

export async function applyContractCreate(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: contractKeys.lists(), refetchType: 'active' }),
    queryClient.invalidateQueries({ queryKey: dashboardKeys.summary(), refetchType: 'active' }),
  ]);
}
