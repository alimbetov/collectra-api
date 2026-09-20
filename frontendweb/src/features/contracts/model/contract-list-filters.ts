import {
  contractSorts, contractStatuses, type ContractListQuery, type ContractSort, type ContractStatus,
} from '../../../entities/contract/model/contract.types';

export interface ContractListState {
  search: string;
  status: '' | ContractStatus;
  externalId: string;
  validFrom: string;
  validTo: string;
  createdFrom: string;
  createdTo: string;
  page: number;
  size: 20 | 50 | 100;
  sort: ContractSort;
}

export const defaultContractListState: ContractListState = {
  search: '', status: '', externalId: '', validFrom: '', validTo: '',
  createdFrom: '', createdTo: '', page: 0, size: 20, sort: 'createdAt,desc',
};

const one = (params: URLSearchParams, key: string) => params.getAll(key).at(-1)?.trim() ?? '';
const date = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : '';

export function parseContractListState(params: URLSearchParams): ContractListState {
  const status = one(params, 'status');
  const sort = one(params, 'sort');
  const size = Number(one(params, 'size'));
  const page = Number(one(params, 'page'));
  return {
    search: one(params, 'search'),
    status: contractStatuses.includes(status as ContractStatus) ? status as ContractStatus : '',
    externalId: one(params, 'externalId'),
    validFrom: date(one(params, 'validFrom')),
    validTo: date(one(params, 'validTo')),
    createdFrom: date(one(params, 'createdFrom')),
    createdTo: date(one(params, 'createdTo')),
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: size === 50 || size === 100 ? size : 20,
    sort: contractSorts.includes(sort as ContractSort) ? sort as ContractSort : 'createdAt,desc',
  };
}

export function serializeContractListState(state: ContractListState): URLSearchParams {
  const params = new URLSearchParams();
  for (const key of ['search', 'status', 'externalId', 'validFrom', 'validTo', 'createdFrom', 'createdTo'] as const) {
    if (state[key]) params.set(key, state[key]);
  }
  if (state.page) params.set('page', String(state.page));
  if (state.size !== 20) params.set('size', String(state.size));
  if (state.sort !== 'createdAt,desc') params.set('sort', state.sort);
  return params;
}

export function updateContractListState(
  state: ContractListState,
  patch: Partial<ContractListState>,
): ContractListState {
  const resetsPage = ['search', 'status', 'externalId', 'validFrom', 'validTo', 'createdFrom', 'createdTo', 'size', 'sort']
    .some((key) => key in patch);
  return { ...state, ...patch, page: resetsPage ? 0 : patch.page ?? state.page };
}

export function hasValidContractRanges(state: ContractListState): boolean {
  return (!state.validFrom || !state.validTo || state.validFrom <= state.validTo)
    && (!state.createdFrom || !state.createdTo || state.createdFrom <= state.createdTo);
}

export function toContractListQuery(state: ContractListState, customerId?: string): ContractListQuery {
  return {
    ...(state.search ? { search: state.search } : {}),
    ...(customerId ? { customerId } : {}),
    ...(state.status ? { status: state.status } : {}),
    ...(state.externalId ? { externalId: state.externalId } : {}),
    ...(state.validFrom ? { validFrom: state.validFrom } : {}),
    ...(state.validTo ? { validTo: state.validTo } : {}),
    ...(state.createdFrom ? { createdFrom: `${state.createdFrom}T00:00:00.000Z` } : {}),
    ...(state.createdTo ? { createdTo: `${state.createdTo}T23:59:59.999Z` } : {}),
    page: state.page, size: state.size, sort: state.sort,
  };
}
