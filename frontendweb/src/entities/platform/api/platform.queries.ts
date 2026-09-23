import { queryOptions } from '@tanstack/react-query';
import type { PlatformTenantListParams } from '../model/platform.types';
import { getPlatformOverview, getPlatformTenant, getPlatformTenants } from './platform.api';

export const platformKeys = {
  all: ['platform'] as const,
  overview: () => [...platformKeys.all, 'overview'] as const,
  tenants: () => [...platformKeys.all, 'tenants'] as const,
  tenantList: (params: PlatformTenantListParams) =>
    [...platformKeys.tenants(), 'list', params] as const,
  tenantDetail: (tenantId: string) => [...platformKeys.tenants(), 'detail', tenantId] as const,
};

export const platformQueries = {
  overview: () =>
    queryOptions({
      queryKey: platformKeys.overview(),
      queryFn: getPlatformOverview,
    }),
  tenants: (params: PlatformTenantListParams) =>
    queryOptions({
      queryKey: platformKeys.tenantList(params),
      queryFn: () => getPlatformTenants(params),
    }),
  tenant: (tenantId: string) =>
    queryOptions({
      queryKey: platformKeys.tenantDetail(tenantId),
      queryFn: () => getPlatformTenant(tenantId),
      enabled: tenantId.length > 0,
    }),
};
