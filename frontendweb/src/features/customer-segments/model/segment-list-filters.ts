import { segmentSorts, type SegmentListQuery, type SegmentSort } from '../../../entities/customer/model/customer.types';

export interface SegmentListState {
  search: string;
  active: '' | 'true' | 'false';
  page: number;
  size: 20 | 50 | 100;
  sort: SegmentSort;
}

export const defaultSegmentListState: SegmentListState = {
  search: '', active: '', page: 0, size: 20, sort: 'name,asc',
};

const one = (params: URLSearchParams, key: string) => params.getAll(key).at(-1)?.trim() ?? '';

export function parseSegmentListState(params: URLSearchParams): SegmentListState {
  const page = Number(one(params, 'page'));
  const size = Number(one(params, 'size'));
  const active = one(params, 'active');
  const sort = one(params, 'sort');
  return {
    search: one(params, 'search'),
    active: active === 'true' || active === 'false' ? active : '',
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: size === 50 || size === 100 ? size : 20,
    sort: segmentSorts.includes(sort as SegmentSort) ? sort as SegmentSort : 'name,asc',
  };
}

export function serializeSegmentListState(state: SegmentListState): URLSearchParams {
  const params = new URLSearchParams();
  if (state.search.trim()) params.set('search', state.search.trim());
  if (state.active) params.set('active', state.active);
  if (state.page) params.set('page', String(state.page));
  if (state.size !== 20) params.set('size', String(state.size));
  if (state.sort !== 'name,asc') params.set('sort', state.sort);
  return params;
}

export function updateSegmentListState(state: SegmentListState, patch: Partial<SegmentListState>): SegmentListState {
  const resetPage = ['search', 'active', 'size', 'sort'].some((key) => key in patch);
  return { ...state, ...patch, page: resetPage ? 0 : patch.page ?? state.page };
}

export function toSegmentListQuery(state: SegmentListState): SegmentListQuery {
  return {
    ...(state.search.trim() ? { search: state.search.trim() } : {}),
    ...(state.active ? { active: state.active === 'true' } : {}),
    page: state.page,
    size: state.size,
    sort: state.sort,
  };
}
