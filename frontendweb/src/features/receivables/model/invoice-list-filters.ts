import type { InvoiceListQuery, PaymentStatus } from '../../../entities/receivable/model/receivable.types';

export interface InvoiceListState {
  search: string;
  paymentStatus: '' | PaymentStatus;
  currency: string;
  dueFrom: string;
  dueTo: string;
  overdue: '' | 'true' | 'false';
  outstandingMin: string;
  outstandingMax: string;
  page: number;
  size: 20 | 50 | 100;
  sort: string;
}

export const defaultInvoiceListState: InvoiceListState = {
  search: '', paymentStatus: '', currency: '', dueFrom: '', dueTo: '', overdue: '',
  outstandingMin: '', outstandingMax: '', page: 0, size: 20, sort: 'createdAt,desc',
};

const one = (p: URLSearchParams, k: string) => p.getAll(k).at(-1)?.trim() ?? '';
const date = (v: string) => /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : '';
const statuses: PaymentStatus[] = ['OPEN', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'];
const sorts = ['createdAt,desc','createdAt,asc','dueDate,asc','dueDate,desc','outstandingAmount,asc','outstandingAmount,desc','invoiceNumber,asc','invoiceNumber,desc'];

export function parseInvoiceListState(p: URLSearchParams): InvoiceListState {
  const status = one(p, 'paymentStatus');
  const size = Number(one(p, 'size'));
  const page = Number(one(p, 'page'));
  const sort = one(p, 'sort');
  const overdue = one(p, 'overdue');
  return {
    search: one(p, 'search'),
    paymentStatus: statuses.includes(status as PaymentStatus) ? status as PaymentStatus : '',
    currency: one(p, 'currency').toUpperCase().slice(0, 3),
    dueFrom: date(one(p, 'dueFrom')), dueTo: date(one(p, 'dueTo')),
    overdue: overdue === 'true' || overdue === 'false' ? overdue : '',
    outstandingMin: one(p, 'outstandingMin'), outstandingMax: one(p, 'outstandingMax'),
    page: Number.isInteger(page) && page >= 0 ? page : 0,
    size: size === 50 || size === 100 ? size : 20,
    sort: sorts.includes(sort) ? sort : 'createdAt,desc',
  };
}

export function serializeInvoiceListState(s: InvoiceListState) {
  const p = new URLSearchParams();
  for (const k of ['search','paymentStatus','currency','dueFrom','dueTo','overdue','outstandingMin','outstandingMax'] as const) if (s[k]) p.set(k, s[k]);
  if (s.page) p.set('page', String(s.page));
  if (s.size !== 20) p.set('size', String(s.size));
  if (s.sort !== 'createdAt,desc') p.set('sort', s.sort);
  return p;
}

export const updateInvoiceListState = (s: InvoiceListState, patch: Partial<InvoiceListState>): InvoiceListState => {
  const reset = Object.keys(patch).some((k) => k !== 'page');
  return { ...s, ...patch, page: reset ? 0 : patch.page ?? s.page };
};

export const validInvoiceRanges = (s: InvoiceListState) =>
  (!s.dueFrom || !s.dueTo || s.dueFrom <= s.dueTo);

export function toInvoiceListQuery(s: InvoiceListState): InvoiceListQuery {
  return {
    ...(s.search ? { search: s.search } : {}),
    ...(s.paymentStatus ? { paymentStatus: s.paymentStatus } : {}),
    ...(s.currency ? { currency: s.currency } : {}),
    ...(s.dueFrom ? { dueFrom: s.dueFrom } : {}),
    ...(s.dueTo ? { dueTo: s.dueTo } : {}),
    ...(s.overdue ? { overdue: s.overdue === 'true' } : {}),
    ...(s.outstandingMin ? { outstandingMin: s.outstandingMin } : {}),
    ...(s.outstandingMax ? { outstandingMax: s.outstandingMax } : {}),
    page: s.page, size: s.size, sort: s.sort,
  };
}
