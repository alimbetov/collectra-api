import { queryOptions } from '@tanstack/react-query';
import { getCustomers, getManagerOptions, getSegmentOptions } from './customer.api';
import type { CustomerListQuery } from '../model/customer.types';

export const customerKeys = {
  all: ['customers'] as const,
  list: (query: CustomerListQuery) => [...customerKeys.all, 'list', query] as const,
  managerOptions: (search: string, page: number) =>
    ['identity', 'user-options', search, page] as const,
  segmentOptions: (search: string, page: number) =>
    ['customer-segments', 'options', search, page] as const,
};

export const customerQueries = {
  list: (query: CustomerListQuery) =>
    queryOptions({ queryKey: customerKeys.list(query), queryFn: () => getCustomers(query) }),
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
