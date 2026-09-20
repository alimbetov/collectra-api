import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { contractKeys } from '../../../entities/contract/api/contract.queries';
import type { ContractDetailDto } from '../../../entities/contract/model/contract.types';
import { dashboardKeys } from '../../../entities/dashboard/api/dashboard.queries';
import { applyContractMutation } from './contract-mutations';

describe('contract mutation cache contract', () => {
  it('sets authoritative detail and targets lists plus dashboard for lifecycle', async () => {
    const client = new QueryClient();
    const invalidate = vi.spyOn(client, 'invalidateQueries').mockResolvedValue();
    const contract = { id: 'contract', status: 'SUSPENDED', version: 2 } as ContractDetailDto;
    await applyContractMutation(client, contract, true);
    expect(client.getQueryData(contractKeys.detail('contract'))).toBe(contract);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: contractKeys.lists(), refetchType: 'active' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: dashboardKeys.summary(), refetchType: 'active' });
  });
});
