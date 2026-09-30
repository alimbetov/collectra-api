import { queryOptions } from '@tanstack/react-query';
import type { InvoiceListQuery, PaymentListQuery } from '../model/receivable.types';
import { getInvoice, getInvoiceAllocations, getInvoices, getPayment, getPaymentAllocations, getPayments } from './receivable.api';

export const receivableKeys = {
  all: ['receivables'] as const,
  invoices: () => [...receivableKeys.all, 'invoices'] as const,
  invoiceLists: () => [...receivableKeys.invoices(), 'list'] as const,
  invoiceList: (query: InvoiceListQuery) => [...receivableKeys.invoiceLists(), query] as const,
  invoiceDetails: () => [...receivableKeys.invoices(), 'detail'] as const,
  invoiceDetail: (invoiceId: string) => [...receivableKeys.invoiceDetails(), invoiceId] as const,
  allocations: (invoiceId: string, page: number) => [...receivableKeys.invoiceDetail(invoiceId), 'allocations', page] as const,
  payments: () => [...receivableKeys.all, 'payments'] as const,
  paymentLists: () => [...receivableKeys.payments(), 'list'] as const,
  paymentList: (query: PaymentListQuery) => [...receivableKeys.paymentLists(), query] as const,
  paymentDetails: () => [...receivableKeys.payments(), 'detail'] as const,
  paymentDetail: (paymentId: string) => [...receivableKeys.paymentDetails(), paymentId] as const,
  paymentAllocations: (paymentId: string, page: number) => [...receivableKeys.paymentDetail(paymentId), 'allocations', page] as const,
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
  payments: (query: PaymentListQuery) => queryOptions({
    queryKey: receivableKeys.paymentList(query),
    queryFn: () => getPayments(query),
  }),
  payment: (paymentId: string) => queryOptions({
    queryKey: receivableKeys.paymentDetail(paymentId),
    queryFn: () => getPayment(paymentId),
  }),
  paymentAllocations: (paymentId: string, page: number) => queryOptions({
    queryKey: receivableKeys.paymentAllocations(paymentId, page),
    queryFn: () => getPaymentAllocations(paymentId, page),
  }),
};
