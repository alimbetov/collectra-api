import { apiRequest } from '../../../shared/api/http-client';
import type { AllocationPageDto, InvoiceCreateCommand, InvoiceDetailDto, InvoiceListQuery, InvoicePageDto } from '../model/receivable.types';

function append(params: URLSearchParams, name: string, value: string | number | boolean | undefined) {
  if (value !== undefined && value !== '') params.set(name, String(value));
}

export function invoiceListPath(query: InvoiceListQuery): string {
  const params = new URLSearchParams();
  append(params, 'customerId', query.customerId);
  append(params, 'contractId', query.contractId);
  append(params, 'paymentStatus', query.paymentStatus);
  append(params, 'currency', query.currency);
  append(params, 'invoiceNumber', query.invoiceNumber);
  append(params, 'externalId', query.externalId);
  append(params, 'search', query.search);
  append(params, 'issuedFrom', query.issuedFrom);
  append(params, 'issuedTo', query.issuedTo);
  append(params, 'dueFrom', query.dueFrom);
  append(params, 'dueTo', query.dueTo);
  append(params, 'overdue', query.overdue);
  append(params, 'amountMin', query.amountMin);
  append(params, 'amountMax', query.amountMax);
  append(params, 'outstandingMin', query.outstandingMin);
  append(params, 'outstandingMax', query.outstandingMax);
  append(params, 'page', query.page);
  append(params, 'size', query.size);
  append(params, 'sort', query.sort);
  const search = params.toString();
  return `/api/v1/invoices${search ? `?${search}` : ''}`;
}

export const getInvoices = (query: InvoiceListQuery) =>
  apiRequest<InvoicePageDto>(invoiceListPath(query));

export const invoiceDetailPath = (invoiceId: string) =>
  `/api/v1/invoices/${encodeURIComponent(invoiceId)}`;

export const getInvoice = (invoiceId: string) =>
  apiRequest<InvoiceDetailDto>(invoiceDetailPath(invoiceId));

export const createInvoice = (command: InvoiceCreateCommand) =>
  apiRequest<InvoiceDetailDto>('/api/v1/invoices', { method: 'POST', body: command });

export const getInvoiceByExternalId = (externalId: string) =>
  apiRequest<InvoiceDetailDto>(`/api/v1/invoices/by-external-id/${encodeURIComponent(externalId)}`);

export const getInvoiceAllocations = (invoiceId: string, page = 0, size = 50) =>
  apiRequest<AllocationPageDto>(
    `${invoiceDetailPath(invoiceId)}/allocations?page=${page}&size=${size}`,
  );
