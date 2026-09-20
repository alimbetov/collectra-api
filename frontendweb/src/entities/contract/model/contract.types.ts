import type { Instant, LocalDate, PageDto, UUID } from '../../../shared/api/contracts';

export const contractStatuses = ['ACTIVE', 'SUSPENDED', 'CLOSED', 'CANCELLED'] as const;
export type ContractStatus = (typeof contractStatuses)[number];

export interface ContractListItemDto {
  id: UUID;
  customerId: UUID;
  customerExternalId: string | null;
  customerDisplayName: string | null;
  externalId: string;
  contractNumber: string;
  status: ContractStatus;
  validFrom: LocalDate;
  validTo: LocalDate | null;
  renewalDate: LocalDate | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface ContractDetailDto extends ContractListItemDto {
  customFields: unknown | null;
}

export type ContractPageDto = PageDto<ContractListItemDto>;

export const contractSorts = [
  'createdAt,desc', 'createdAt,asc', 'updatedAt,desc', 'updatedAt,asc',
  'contractNumber,asc', 'contractNumber,desc', 'externalId,asc', 'externalId,desc',
  'status,asc', 'status,desc', 'validFrom,asc', 'validFrom,desc',
  'validTo,asc', 'validTo,desc', 'renewalDate,asc', 'renewalDate,desc',
] as const;
export type ContractSort = (typeof contractSorts)[number];

export interface ContractListQuery {
  search?: string;
  customerId?: UUID;
  status?: ContractStatus;
  externalId?: string;
  validFrom?: LocalDate;
  validTo?: LocalDate;
  createdFrom?: Instant;
  createdTo?: Instant;
  page?: number;
  size?: number;
  sort?: ContractSort;
}

export interface ContractCreateCommand {
  customerId: UUID;
  externalId: string;
  contractNumber: string;
  validFrom: LocalDate;
  validTo: LocalDate | null;
  renewalDate: LocalDate | null;
  customFields: unknown | null;
}

export interface ContractUpdateCommand {
  contractNumber: string;
  validFrom: LocalDate;
  validTo: LocalDate | null;
  renewalDate: LocalDate | null;
  customFields: unknown | null;
  version: number;
}

export type ContractLifecycleAction = 'suspend' | 'activate' | 'close' | 'cancel';
