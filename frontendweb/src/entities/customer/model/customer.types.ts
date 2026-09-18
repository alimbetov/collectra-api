import type { Instant, PageDto, UUID } from '../../../shared/api/contracts';

export const customerStatuses = ['ACTIVE', 'INACTIVE', 'BLOCKED', 'ARCHIVED'] as const;
export type CustomerStatus = (typeof customerStatuses)[number];

export const customerTypes = ['INDIVIDUAL', 'COMPANY'] as const;
export type CustomerType = (typeof customerTypes)[number];

export interface SegmentSummaryDto {
  id: UUID;
  code: string;
  name: string;
}

export interface CustomerListItemDto {
  id: UUID;
  externalId: string;
  customerType: CustomerType;
  displayName: string;
  status: CustomerStatus;
  managerUserId: UUID | null;
  preferredLocale: string | null;
  timezone: string | null;
  segmentIds: UUID[];
  primaryEmail: string | null;
  primaryPhone: string | null;
  managerDisplayName: string | null;
  segments: SegmentSummaryDto[];
  createdAt: Instant;
  updatedAt: Instant;
}

export type CustomerPageDto = PageDto<CustomerListItemDto>;

export interface CustomerListQuery {
  search?: string;
  status?: CustomerStatus;
  customerType?: CustomerType;
  managerId?: UUID;
  segmentId?: UUID;
  externalId?: string;
  email?: string;
  phone?: string;
  createdFrom?: Instant;
  createdTo?: Instant;
  page?: number;
  size?: number;
  sort?: CustomerSort;
}

export const customerSorts = [
  'createdAt,desc',
  'createdAt,asc',
  'updatedAt,desc',
  'updatedAt,asc',
  'displayName,asc',
  'displayName,desc',
  'externalId,asc',
  'externalId,desc',
] as const;
export type CustomerSort = (typeof customerSorts)[number];

export interface UserOptionDto {
  userId: UUID;
  label: string;
  email: string;
  displayName: string | null;
  status: 'ACTIVE' | 'BLOCKED';
}

export type UserOptionPageDto = PageDto<UserOptionDto>;

export interface SegmentOptionDto {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type SegmentOptionPageDto = PageDto<SegmentOptionDto>;
