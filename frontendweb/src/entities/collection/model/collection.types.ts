import type { DecimalString, Instant, LocalDate, PageDto, UUID } from '../../../shared/api/contracts';

export type CollectionCaseStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'CLOSED';
export type CollectionPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type CollectionCloseReason = 'PAID' | 'SETTLED' | 'WRITTEN_OFF' | 'DUPLICATE' | 'CANCELLED' | 'OTHER';

export interface CollectionCaseItemDto {
  id: UUID; customerId: UUID; customerDisplayName: string; invoiceId: UUID; invoiceNumber: string;
  status: CollectionCaseStatus; priority: CollectionPriority; assignedTo?: UUID | null; assigneeDisplayName?: string | null;
  currency: string; outstandingAmount: DecimalString; paymentStatus: string; nextActionType?: string | null;
  nextActionDueAt?: Instant | null; nextActionOverdue: boolean; openedAt: Instant; closedAt?: Instant | null;
  closeReason?: CollectionCloseReason | null; version: number;
}
export type CollectionCasePageDto = PageDto<CollectionCaseItemDto>;
export interface CollectionCaseDto {
  id: UUID; customerId: UUID; invoiceId: UUID; status: CollectionCaseStatus; priority: CollectionPriority;
  assignedTo?: UUID | null; openedAt: Instant; closedAt?: Instant | null; closeReason?: CollectionCloseReason | null; version: number;
}
export interface CollectionListQuery {
  customerId?: UUID; invoiceId?: UUID; status?: CollectionCaseStatus; priority?: CollectionPriority; assignedTo?: UUID;
  nextActionOverdue?: boolean; nextActionDueFrom?: Instant; nextActionDueTo?: Instant; page: number; size: number; sort: string;
}
export interface PromiseDto { id: UUID; amount: DecimalString; currency: string; promisedDate: LocalDate; status: string; overdue: boolean; businessDate: LocalDate; resolvedAt?: Instant | null; version: number; }
export interface DisputeDto { id: UUID; reason: string; description?: string | null; status: string; resolutionCode?: string | null; resolutionSummary?: string | null; resolvedAt?: Instant | null; resolvedBy?: string | null; version: number; }
export interface CollectionActionDto { id: UUID; actionType: string; description?: string | null; dueAt: Instant; priority: CollectionPriority; status: string; overdue: boolean; asOf: Instant; version: number; }
export interface TimelineItemDto { eventId: UUID; eventType: string; entityType: string; entityId: UUID; eventAt: Instant; actor: string; summary: string; }
export type HistoryPageDto<T> = PageDto<T>;
