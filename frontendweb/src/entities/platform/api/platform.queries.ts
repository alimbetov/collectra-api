import { queryOptions } from '@tanstack/react-query';
import type {
  PlatformAdministratorListParams,
  PlatformTenantListParams,
  PlatformUserListParams,
} from '../model/platform.types';
import {
  getPlatformAdministrators,
  getPlatformMembershipSessions,
  getPlatformOverview,
  getPlatformTenant,
  getPlatformTenants,
  getPlatformUser,
  getPlatformUsers,
} from './platform.api';

export const platformKeys = {
  all: ['platform'] as const,
  overview: () => [...platformKeys.all, 'overview'] as const,
  tenants: () => [...platformKeys.all, 'tenants'] as const,
  tenantList: (params: PlatformTenantListParams) =>
    [...platformKeys.tenants(), 'list', params] as const,
  tenantDetail: (tenantId: string) => [...platformKeys.tenants(), 'detail', tenantId] as const,
  users: () => [...platformKeys.all, 'users'] as const,
  userList: (params: PlatformUserListParams) => [...platformKeys.users(), 'list', params] as const,
  userDetail: (userId: string) => [...platformKeys.users(), 'detail', userId] as const,
  membershipSessions: (membershipId: string, page: number, size: number) =>
    [...platformKeys.users(), 'sessions', membershipId, page, size] as const,
  administrators: () => [...platformKeys.all, 'administrators'] as const,
  administratorList: (params: PlatformAdministratorListParams) =>
    [...platformKeys.administrators(), 'list', params] as const,
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
  users: (params: PlatformUserListParams) =>
    queryOptions({
      queryKey: platformKeys.userList(params),
      queryFn: () => getPlatformUsers(params),
    }),
  user: (userId: string) =>
    queryOptions({
      queryKey: platformKeys.userDetail(userId),
      queryFn: () => getPlatformUser(userId),
      enabled: userId.length > 0,
    }),
  membershipSessions: (membershipId: string, page = 0, size = 50) =>
    queryOptions({
      queryKey: platformKeys.membershipSessions(membershipId, page, size),
      queryFn: () => getPlatformMembershipSessions(membershipId, page, size),
      enabled: membershipId.length > 0,
    }),
  administrators: (params: PlatformAdministratorListParams) =>
    queryOptions({
      queryKey: platformKeys.administratorList(params),
      queryFn: () => getPlatformAdministrators(params),
    }),
};
