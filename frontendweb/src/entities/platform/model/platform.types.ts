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
