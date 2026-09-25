import { queryOptions } from '@tanstack/react-query';
import type { InvoiceListQuery } from '../model/receivable.types';
import { getInvoice, getInvoiceAllocations, getInvoices } from './receivable.api';

export const receivableKeys = {
  all: ['receivables'] as const,
  invoices: () => [...receivableKeys.all, 'invoices'] as const,
  invoiceLists: () => [...receivableKeys.invoices(), 'list'] as const,
  invoiceList: (query: InvoiceListQuery) => [...receivableKeys.invoiceLists(), query] as const,
  invoiceDetails: () => [...receivableKeys.invoices(), 'detail'] as const,
  invoiceDetail: (invoiceId: string) => [...receivableKeys.invoiceDetails(), invoiceId] as const,
  allocations: (invoiceId: string, page: number) => [...receivableKeys.invoiceDetail(invoiceId), 'allocations', page] as const,
};

export const receivableQueries = {
  invoices: (query: InvoiceListQuery) => queryOptions({
    queryKey: receivableKeys.invoiceList(query),
    queryFn: () => getInvoices(query),
  }),
  invoice: (invoiceId: string) => queryOptions({
    queryKey: receivableKeys.invoiceDetail(invoiceId),
    queryFn: () => getInvoice(invoiceId),
  }),
  allocations: (invoiceId: string, page: number) => queryOptions({
    queryKey: receivableKeys.allocations(invoiceId, page),
    queryFn: () => getInvoiceAllocations(invoiceId, page),
  }),
};
