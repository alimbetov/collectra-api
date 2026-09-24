export interface PlatformChannelCountDto {
  channel: string;
  messages: number;
}

export interface PlatformOverviewDto {
  generatedAt: string;
  periodFrom: string;
  tenantsTotal: number;
  tenantsActive: number;
  tenantsBlocked: number;
  usersTotal: number;
  usersActive: number;
  usersBlocked: number;
  campaignsLast30Days: number;
  messagesLast30Days: number;
  sentLast30Days: number;
  failedLast30Days: number;
  retryWaitCurrent: number;
  unknownCurrent: number;
  channels: PlatformChannelCountDto[];
}

export type PlatformTenantStatus = 'ACTIVE' | 'BLOCKED';

export interface PlatformTenantListItemDto {
  id: string;
  slug: string;
  name: string;
  status: PlatformTenantStatus;
  createdAt: string;
  updatedAt: string;
  revision: number;
  activeUsers: number;
  campaignsLast30d: number;
  messagesLast30d: number;
}

export interface PlatformTenantDetailDto {
  id: string;
  slug: string;
  name: string;
  status: PlatformTenantStatus;
  createdAt: string;
  updatedAt: string;
  revision: number;
  activeUsers: number;
  blockedUsers: number;
  customers: number;
  campaigns: number;
  messages: number;
  generatedDocuments: number;
  files: number;
}

export interface PlatformTenantPageDto {
  items: PlatformTenantListItemDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PlatformTenantListParams {
  search?: string;
  status?: PlatformTenantStatus;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PlatformTenantStatusCommand {
  active: boolean;
  revision: number;
  reason: string;
}


export type PlatformAccountStatus = 'ACTIVE' | 'BLOCKED';
export type PlatformMembershipStatus = 'ACTIVE' | 'BLOCKED';
export type PlatformEffectiveAccessStatus = 'ACTIVE' | 'BLOCKED';

export interface PlatformUserListItemDto {
  userId: string;
  membershipId: string;
  tenantId: string;
  tenantSlug: string;
  tenantName: string;
  email: string;
  displayName: string | null;
  accountStatus: PlatformAccountStatus;
  membershipStatus: PlatformMembershipStatus;
  effectiveAccessStatus: PlatformEffectiveAccessStatus;
  roleCodes: string[];
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface PlatformUserDetailDto extends PlatformUserListItemDto {
  locale: string | null;
  timezone: string | null;
  authorizationVersion: number;
  sessionCount: number;
  activeSessionCount: number;
}

export interface PlatformUserPageDto {
  items: PlatformUserListItemDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PlatformUserListParams {
  search?: string;
  tenantId?: string;
  tenantSlug?: string;
  membershipStatus?: PlatformMembershipStatus;
  accountStatus?: PlatformAccountStatus;
  roleCode?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PlatformMembershipStatusCommand {
  active: boolean;
  revision: number;
  reason: string;
}

export interface PlatformSessionDto {
  id: string;
  familyId: string;
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  lastUsedAt: string | null;
  userAgent: string | null;
  sourceIp: string | null;
}

export interface PlatformSessionPageDto {
  items: PlatformSessionDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PlatformAdministratorDto {
  id: string;
  email: string;
  status: PlatformAccountStatus;
  authorizationVersion: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface PlatformAdministratorPageDto {
  items: PlatformAdministratorDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PlatformAdministratorListParams {
  search?: string;
  status?: PlatformAccountStatus;
  page?: number;
  size?: number;
  sort?: string;
}
