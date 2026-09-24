import { apiRequest } from '../../../shared/api/http-client';
import type {
  PlatformOverviewDto,
  PlatformTenantDetailDto,
  PlatformTenantListParams,
  PlatformTenantPageDto,
  PlatformTenantStatusCommand,
  PlatformUserDetailDto,
  PlatformUserListParams,
  PlatformUserPageDto,
  PlatformMembershipStatusCommand,
  PlatformSessionPageDto,
  PlatformAdministratorListParams,
  PlatformAdministratorPageDto,
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


function userListQuery(params: PlatformUserListParams): string {
  const query = new URLSearchParams();
  if (params.search) query.set('search', params.search);
  if (params.tenantId) query.set('tenantId', params.tenantId);
  if (params.tenantSlug) query.set('tenantSlug', params.tenantSlug);
  if (params.membershipStatus) query.set('membershipStatus', params.membershipStatus);
  if (params.accountStatus) query.set('accountStatus', params.accountStatus);
  if (params.roleCode) query.set('roleCode', params.roleCode);
  if (params.createdFrom) query.set('createdFrom', params.createdFrom);
  if (params.createdTo) query.set('createdTo', params.createdTo);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 50));
  query.set('sort', params.sort ?? 'createdAt,desc');
  return query.toString();
}

export const getPlatformUsers = (params: PlatformUserListParams) =>
  apiRequest<PlatformUserPageDto>(`/api/v1/platform/users?${userListQuery(params)}`);

export const getPlatformUser = (userId: string) =>
  apiRequest<PlatformUserDetailDto>(`/api/v1/platform/users/${userId}`);

export const changePlatformMembershipStatus = (
  membershipId: string,
  command: PlatformMembershipStatusCommand,
) =>
  apiRequest<PlatformUserDetailDto>(`/api/v1/platform/memberships/${membershipId}/status`, {
    method: 'PATCH',
    body: command,
  });

export const getPlatformMembershipSessions = (
  membershipId: string,
  page = 0,
  size = 50,
) =>
  apiRequest<PlatformSessionPageDto>(
    `/api/v1/platform/memberships/${membershipId}/sessions?page=${page}&size=${size}`,
  );

export const revokePlatformMembershipSessions = (membershipId: string, reason: string) => {
  const query = new URLSearchParams({ reason });
  return apiRequest<PlatformUserDetailDto>(
    `/api/v1/platform/memberships/${membershipId}/sessions?${query.toString()}`,
    { method: 'DELETE' },
  );
};

function administratorListQuery(params: PlatformAdministratorListParams): string {
  const query = new URLSearchParams();
  if (params.search) query.set('search', params.search);
  if (params.status) query.set('status', params.status);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 50));
  query.set('sort', params.sort ?? 'email,asc');
  return query.toString();
}

export const getPlatformAdministrators = (params: PlatformAdministratorListParams) =>
  apiRequest<PlatformAdministratorPageDto>(
    `/api/v1/platform/administrators?${administratorListQuery(params)}`,
  );
