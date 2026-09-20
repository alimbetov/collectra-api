import type { ContractLifecycleAction, ContractStatus } from '../../../entities/contract/model/contract.types';

export const allowedContractActions: Record<ContractStatus, readonly ContractLifecycleAction[]> = {
  ACTIVE: ['suspend', 'close', 'cancel'],
  SUSPENDED: ['activate', 'close', 'cancel'],
  CLOSED: [],
  CANCELLED: [],
};
