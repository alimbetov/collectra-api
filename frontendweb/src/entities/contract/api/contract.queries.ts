import { queryOptions } from '@tanstack/react-query';
import type { ContractListQuery } from '../model/contract.types';
import { getContract, getContracts } from './contract.api';

export const contractKeys = {
  all: ['contracts'] as const,
  lists: () => [...contractKeys.all, 'list'] as const,
  list: (query: ContractListQuery) => [...contractKeys.lists(), query] as const,
  details: () => [...contractKeys.all, 'detail'] as const,
  detail: (contractId: string) => [...contractKeys.details(), contractId] as const,
};

export const contractQueries = {
  list: (query: ContractListQuery) => queryOptions({
    queryKey: contractKeys.list(query), queryFn: () => getContracts(query),
  }),
  detail: (contractId: string) => queryOptions({
    queryKey: contractKeys.detail(contractId), queryFn: () => getContract(contractId),
  }),
};
