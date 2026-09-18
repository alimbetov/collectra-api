import {
  customerSorts,
  customerStatuses,
  customerTypes,
  type CustomerListQuery,
  type CustomerSort,
  type CustomerStatus,
  type CustomerType,
} from '../../../entities/customer/model/customer.types';

export const DEFAULT_CUSTOMER_SIZE = 50;
export const DEFAULT_CUSTOMER_SORT: CustomerSort = 'createdAt,desc';
export const customerPageSizes = [25, 50, 100] as const;

export interface CustomerListState {
  search: string;
  status: CustomerStatus | '';
  customerType: CustomerType | '';
  managerId: string;
  segmentId: string;
  externalId: string;
  email: string;
  phone: string;
  createdFrom: string;
  createdTo: string;
  page: number;
  size: (typeof customerPageSizes)[number];
  sort: CustomerSort;
}

export const defaultCustomerListState: CustomerListState = {
  search: '',
  status: '',
  customerType: '',
  managerId: '',
  segmentId: '',
  externalId: '',
  email: '',
  phone: '',
  createdFrom: '',
  createdTo: '',
  page: 0,
  size: DEFAULT_CUSTOMER_SIZE,
  sort: DEFAULT_CUSTOMER_SORT,
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function text(params: URLSearchParams, name: string): string {
  return params.get(name)?.trim() ?? '';
}

function oneOf<T extends string>(value: string, allowed: readonly T[]): T | '' {
  return allowed.includes(value as T) ? (value as T) : '';
}

function instant(value: string): string {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? '' : date.toISOString();
}

export function parseCustomerListState(params: URLSearchParams): CustomerListState {
  const rawPage = Number(params.get('page'));
  const rawSize = Number(params.get('size'));
  const managerId = text(params, 'managerId');
  const segmentId = text(params, 'segmentId');
  return {
    search: text(params, 'search'),
    status: oneOf(text(params, 'status'), customerStatuses),
    customerType: oneOf(text(params, 'customerType'), customerTypes),
    managerId: uuidPattern.test(managerId) ? managerId : '',
    segmentId: uuidPattern.test(segmentId) ? segmentId : '',
    externalId: text(params, 'externalId'),
    email: text(params, 'email'),
    phone: text(params, 'phone'),
    createdFrom: instant(text(params, 'createdFrom')),
    createdTo: instant(text(params, 'createdTo')),
    page: Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0,
    size: customerPageSizes.includes(rawSize as CustomerListState['size'])
      ? (rawSize as CustomerListState['size'])
      : DEFAULT_CUSTOMER_SIZE,
    sort: oneOf(text(params, 'sort'), customerSorts) || DEFAULT_CUSTOMER_SORT,
  };
}

function setText(params: URLSearchParams, name: string, value: string) {
  const normalized = value.trim();
  if (normalized) params.set(name, normalized);
}

export function serializeCustomerListState(value: CustomerListState): URLSearchParams {
  const params = new URLSearchParams();
  setText(params, 'search', value.search);
  setText(params, 'status', value.status);
  setText(params, 'customerType', value.customerType);
  setText(params, 'managerId', value.managerId);
  setText(params, 'segmentId', value.segmentId);
  setText(params, 'externalId', value.externalId);
  setText(params, 'email', value.email);
  setText(params, 'phone', value.phone);
  setText(params, 'createdFrom', value.createdFrom);
  setText(params, 'createdTo', value.createdTo);
  if (value.page !== 0) params.set('page', String(value.page));
  if (value.size !== DEFAULT_CUSTOMER_SIZE) params.set('size', String(value.size));
  if (value.sort !== DEFAULT_CUSTOMER_SORT) params.set('sort', value.sort);
  return params;
}

export function updateCustomerListState(
  value: CustomerListState,
  patch: Partial<CustomerListState>,
  resetPage = true,
): CustomerListState {
  return { ...value, ...patch, page: resetPage ? 0 : (patch.page ?? value.page) };
}

export function toCustomerListQuery(value: CustomerListState): CustomerListQuery {
  const query: CustomerListQuery = {};
  if (value.search) query.search = value.search;
  if (value.status) query.status = value.status;
  if (value.customerType) query.customerType = value.customerType;
  if (value.managerId) query.managerId = value.managerId;
  if (value.segmentId) query.segmentId = value.segmentId;
  if (value.externalId) query.externalId = value.externalId;
  if (value.email) query.email = value.email;
  if (value.phone) query.phone = value.phone;
  if (value.createdFrom) query.createdFrom = value.createdFrom;
  if (value.createdTo) query.createdTo = value.createdTo;
  if (value.page !== 0) query.page = value.page;
  if (value.size !== DEFAULT_CUSTOMER_SIZE) query.size = value.size;
  if (value.sort !== DEFAULT_CUSTOMER_SORT) query.sort = value.sort;
  return query;
}

export function hasCustomerFilters(value: CustomerListState): boolean {
  return Boolean(
    value.search ||
      value.status ||
      value.customerType ||
      value.managerId ||
      value.segmentId ||
      value.externalId ||
      value.email ||
      value.phone ||
      value.createdFrom ||
      value.createdTo,
  );
}

export function hasValidCreatedRange(value: CustomerListState): boolean {
  return !value.createdFrom || !value.createdTo || value.createdFrom <= value.createdTo;
}

export function instantToLocalInput(value: string, timeZone: string): string {
  if (!value) return '';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(value));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((candidate) => candidate.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}T${part('hour')}:${part('minute')}`;
}

export function localInputToInstant(value: string, timeZone: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) return '';
  const target = Date.UTC(+match[1], +match[2] - 1, +match[3], +match[4], +match[5]);
  let guess = target;
  for (let iteration = 0; iteration < 2; iteration += 1) {
    const rendered = instantToLocalInput(new Date(guess).toISOString(), timeZone);
    const renderedMatch = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(rendered);
    if (!renderedMatch) return '';
    const renderedUtc = Date.UTC(
      +renderedMatch[1],
      +renderedMatch[2] - 1,
      +renderedMatch[3],
      +renderedMatch[4],
      +renderedMatch[5],
    );
    guess += target - renderedUtc;
  }
  const result = new Date(guess).toISOString();
  return instantToLocalInput(result, timeZone) === value ? result : '';
}
