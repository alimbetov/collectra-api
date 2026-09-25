import { queryOptions } from '@tanstack/react-query';
import type { InvoiceListQuery } from '../model/receivable.types';
import { getInvoice, getInvoices } from './receivable.api';

export const receivableKeys = {
  all: ['receivables'] as const,
  invoices: () => [...receivableKeys.all, 'invoices'] as const,
  invoiceLists: () => [...receivableKeys.invoices(), 'list'] as const,
  invoiceList: (query: InvoiceListQuery) => [...receivableKeys.invoiceLists(), query] as const,
  invoiceDetails: () => [...receivableKeys.invoices(), 'detail'] as const,
  invoiceDetail: (invoiceId: string) => [...receivableKeys.invoiceDetails(), invoiceId] as const,
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
};
