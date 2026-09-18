import { queryOptions } from '@tanstack/react-query';
import {
  getCustomer,
  getCustomerEmails,
  getCustomerPhones,
  getCustomers,
  getManagerOptions,
  getSegmentOptions,
} from './customer.api';
import type { CustomerListQuery } from '../model/customer.types';

export const customerKeys = {
  all: ['customers'] as const,
  lists: () => [...customerKeys.all, 'list'] as const,
  list: (query: CustomerListQuery) => [...customerKeys.lists(), query] as const,
  detail: (customerId: string) => [...customerKeys.all, 'detail', customerId] as const,
  emails: (customerId: string) => [...customerKeys.detail(customerId), 'emails'] as const,
  phones: (customerId: string) => [...customerKeys.detail(customerId), 'phones'] as const,
  managerOptions: (search: string, page: number) =>
    ['identity', 'user-options', search, page] as const,
  segmentOptions: (search: string, page: number) =>
    ['customer-segments', 'options', search, page] as const,
};

export const customerQueries = {
  list: (query: CustomerListQuery) =>
    queryOptions({ queryKey: customerKeys.list(query), queryFn: () => getCustomers(query) }),
  detail: (customerId: string) =>
    queryOptions({ queryKey: customerKeys.detail(customerId), queryFn: () => getCustomer(customerId) }),
  emails: (customerId: string) =>
    queryOptions({ queryKey: customerKeys.emails(customerId), queryFn: () => getCustomerEmails(customerId) }),
  phones: (customerId: string) =>
    queryOptions({ queryKey: customerKeys.phones(customerId), queryFn: () => getCustomerPhones(customerId) }),
  managerOptions: (search: string, page = 0) =>
    queryOptions({
      queryKey: customerKeys.managerOptions(search, page),
      queryFn: () => getManagerOptions(search, page),
    }),
  segmentOptions: (search: string, page = 0) =>
    queryOptions({
      queryKey: customerKeys.segmentOptions(search, page),
      queryFn: () => getSegmentOptions(search, page),
    }),
};
