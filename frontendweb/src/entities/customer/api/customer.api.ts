import { apiRequest } from '../../../shared/api/http-client';
import type {
  CustomerListQuery,
  CustomerPageDto,
  SegmentOptionPageDto,
  UserOptionPageDto,
} from '../model/customer.types';

function append(params: URLSearchParams, name: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') params.set(name, String(value));
}

export function customerListPath(query: CustomerListQuery): string {
  const params = new URLSearchParams();
  append(params, 'search', query.search);
  append(params, 'status', query.status);
  append(params, 'customerType', query.customerType);
  append(params, 'managerId', query.managerId);
  append(params, 'segmentId', query.segmentId);
  append(params, 'externalId', query.externalId);
  append(params, 'email', query.email);
  append(params, 'phone', query.phone);
  append(params, 'createdFrom', query.createdFrom);
  append(params, 'createdTo', query.createdTo);
  append(params, 'page', query.page);
  append(params, 'size', query.size);
  append(params, 'sort', query.sort);
  const search = params.toString();
  return `/api/v1/customers${search ? `?${search}` : ''}`;
}

export const getCustomers = (query: CustomerListQuery) =>
  apiRequest<CustomerPageDto>(customerListPath(query));

export const getManagerOptions = (search: string, page = 0) => {
  const params = new URLSearchParams({ status: 'ACTIVE', page: String(page), size: '20' });
  if (search.trim()) params.set('search', search.trim());
  return apiRequest<UserOptionPageDto>(`/api/v1/identity/user-options?${params}`);
};

export const getSegmentOptions = (search: string, page = 0) => {
  const params = new URLSearchParams({
    active: 'true',
    page: String(page),
    size: '20',
    sort: 'name,asc',
  });
  if (search.trim()) params.set('search', search.trim());
  return apiRequest<SegmentOptionPageDto>(`/api/v1/customer-segments?${params}`);
};
