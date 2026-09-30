import { apiRequest } from '../../../shared/api/http-client';
import type { AllocationCreateCommand, AllocationDto, AllocationPageDto, AllocationReverseCommand, InvoiceCreateCommand, InvoiceDetailDto, InvoiceListQuery, InvoicePageDto, PaymentCreateCommand, PaymentDetailDto, PaymentListQuery, PaymentPageDto } from '../model/receivable.types';

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


export function paymentListPath(query: PaymentListQuery): string {
  const params = new URLSearchParams();
  append(params, 'customerId', query.customerId);
  append(params, 'invoiceId', query.invoiceId);
  append(params, 'currency', query.currency);
  append(params, 'paymentReference', query.paymentReference);
  append(params, 'externalId', query.externalId);
  append(params, 'paymentFrom', query.paymentFrom);
  append(params, 'paymentTo', query.paymentTo);
  append(params, 'amountMin', query.amountMin);
  append(params, 'amountMax', query.amountMax);
  append(params, 'unallocatedOnly', query.unallocatedOnly);
  append(params, 'search', query.search);
  append(params, 'page', query.page);
  append(params, 'size', query.size);
  append(params, 'sort', query.sort);
  const search = params.toString();
  return `/api/v1/payments${search ? `?${search}` : ''}`;
}

export const getPayments = (query: PaymentListQuery) =>
  apiRequest<PaymentPageDto>(paymentListPath(query));

export const getPayment = (paymentId: string) =>
  apiRequest<PaymentDetailDto>(`/api/v1/payments/${encodeURIComponent(paymentId)}`);

export const createPayment = (command: PaymentCreateCommand) =>
  apiRequest<PaymentDetailDto>('/api/v1/payments', { method: 'POST', body: command });

export const getPaymentAllocations = (paymentId: string, page = 0, size = 50) =>
  apiRequest<AllocationPageDto>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}/allocations?page=${page}&size=${size}`,
  );

export const allocatePayment = (paymentId: string, command: AllocationCreateCommand) =>
  apiRequest<AllocationDto>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}/allocations`,
    { method: 'POST', body: command },
  );

export const reversePaymentAllocation = (
  paymentId: string,
  allocationId: string,
  command: AllocationReverseCommand,
) =>
  apiRequest<AllocationDto>(
    `/api/v1/payments/${encodeURIComponent(paymentId)}/allocations/${encodeURIComponent(allocationId)}/reverse`,
    { method: 'POST', body: command },
  );
