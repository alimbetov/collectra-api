import { queryOptions } from '@tanstack/react-query';
import {
  getCustomer,
  getCustomerEmails,
  getCustomerPhones,
  getCustomers,
  getManagerOptions,
  getSegmentOptions,
  getSegment,
  getSegments,
} from './customer.api';
import type { CustomerListQuery, SegmentListQuery } from '../model/customer.types';

export const segmentKeys = {
  all: ['customer-segments'] as const,
  lists: () => [...segmentKeys.all, 'list'] as const,
  list: (query: SegmentListQuery) => [...segmentKeys.lists(), query] as const,
  details: () => [...segmentKeys.all, 'detail'] as const,
  detail: (segmentId: string) => [...segmentKeys.all, 'detail', segmentId] as const,
  optionsRoot: () => [...segmentKeys.all, 'options'] as const,
  options: (search: string, page: number) => [...segmentKeys.all, 'options', search, page] as const,
};

export const customerKeys = {
  all: ['customers'] as const,
  lists: () => [...customerKeys.all, 'list'] as const,
  list: (query: CustomerListQuery) => [...customerKeys.lists(), query] as const,
  detail: (customerId: string) => [...customerKeys.all, 'detail', customerId] as const,
  emails: (customerId: string) => [...customerKeys.detail(customerId), 'emails'] as const,
  phones: (customerId: string) => [...customerKeys.detail(customerId), 'phones'] as const,
  managerOptions: (search: string, page: number) =>
    ['identity', 'user-options', search, page] as const,
  segmentOptions: (search: string, page: number) => segmentKeys.options(search, page),
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
  segmentList: (query: SegmentListQuery) =>
    queryOptions({ queryKey: segmentKeys.list(query), queryFn: () => getSegments(query) }),
  segmentDetail: (segmentId: string) =>
    queryOptions({ queryKey: segmentKeys.detail(segmentId), queryFn: () => getSegment(segmentId) }),
};
