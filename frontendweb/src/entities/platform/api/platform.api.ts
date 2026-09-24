import { apiRequest } from '../../../shared/api/http-client';
import type {
  PlatformOverviewDto,
  PlatformTenantDetailDto,
  PlatformTenantListParams,
  PlatformTenantPageDto,
  PlatformTenantStatusCommand,
} from '../model/platform.types';

export const getPlatformOverview = () =>
  apiRequest<PlatformOverviewDto>('/api/v1/platform/overview');

function tenantListQuery(params: PlatformTenantListParams): string {
  const query = new URLSearchParams();
  if (params.search) query.set('search', params.search);
  if (params.status) query.set('status', params.status);
  if (params.createdFrom) query.set('createdFrom', params.createdFrom);
  if (params.createdTo) query.set('createdTo', params.createdTo);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 50));
  query.set('sort', params.sort ?? 'createdAt,desc');
  return query.toString();
}

export const getPlatformTenants = (params: PlatformTenantListParams) =>
  apiRequest<PlatformTenantPageDto>(`/api/v1/platform/tenants?${tenantListQuery(params)}`);

export const getPlatformTenant = (tenantId: string) =>
  apiRequest<PlatformTenantDetailDto>(`/api/v1/platform/tenants/${tenantId}`);

export const changePlatformTenantStatus = (
  tenantId: string,
  command: PlatformTenantStatusCommand,
) =>
  apiRequest<PlatformTenantDetailDto>(`/api/v1/platform/tenants/${tenantId}/status`, {
    method: 'PATCH',
    body: command,
  });
