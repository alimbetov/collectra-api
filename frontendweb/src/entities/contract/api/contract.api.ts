import { apiRequest } from '../../../shared/api/http-client';
import type {
  ContractCreateCommand, ContractDetailDto, ContractLifecycleAction, ContractListQuery,
  ContractPageDto, ContractUpdateCommand,
} from '../model/contract.types';

function append(params: URLSearchParams, name: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') params.set(name, String(value));
}

export function contractListPath(query: ContractListQuery): string {
  const params = new URLSearchParams();
  append(params, 'search', query.search);
  append(params, 'customerId', query.customerId);
  append(params, 'status', query.status);
  append(params, 'externalId', query.externalId);
  append(params, 'validFrom', query.validFrom);
  append(params, 'validTo', query.validTo);
  append(params, 'createdFrom', query.createdFrom);
  append(params, 'createdTo', query.createdTo);
  append(params, 'page', query.page);
  append(params, 'size', query.size);
  append(params, 'sort', query.sort);
  const search = params.toString();
  return `/api/v1/contracts${search ? `?${search}` : ''}`;
}

export const contractDetailPath = (contractId: string) =>
  `/api/v1/contracts/${encodeURIComponent(contractId)}`;

export const getContracts = (query: ContractListQuery) =>
  apiRequest<ContractPageDto>(contractListPath(query));

export const getContract = (contractId: string) =>
  apiRequest<ContractDetailDto>(contractDetailPath(contractId));

export const createContract = (command: ContractCreateCommand) =>
  apiRequest<ContractDetailDto>('/api/v1/contracts', { method: 'POST', body: command });

export const updateContract = (contractId: string, command: ContractUpdateCommand) =>
  apiRequest<ContractDetailDto>(contractDetailPath(contractId), { method: 'PUT', body: command });

export const transitionContract = (
  contractId: string,
  action: ContractLifecycleAction,
  version: number,
) => apiRequest<ContractDetailDto>(`${contractDetailPath(contractId)}/${action}`, {
  method: 'POST', body: { version },
});
