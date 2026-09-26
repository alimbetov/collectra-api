import type { Instant, UUID } from '../../../shared/api/contracts';

export type MessageStatus = 'PENDING' | 'PROCESSING' | 'RETRY_WAIT' | 'SENT' | 'FAILED' | 'SKIPPED' | string;
export type MessageChannel = 'EMAIL' | 'SMS' | 'TELEGRAM' | 'WHATSAPP' | string;
export type AttachmentStatus = 'PENDING' | 'READY' | 'FAILED' | string;

export interface MessageListItemDto {
  id: UUID;
  campaignRunId: UUID;
  customerId: UUID;
  channel: MessageChannel;
  maskedDestination: string;
  status: MessageStatus;
  attemptCount: number;
  nextRetryAt: Instant | null;
  sentAt: Instant | null;
  createdAt: Instant;
}
export interface MessageSliceDto { content: MessageListItemDto[]; page: number; size: number; hasNext: boolean; }
export interface MessageAttachmentDto { id: UUID; filename: string; contentType: string | null; size: number | null; required: boolean; status: AttachmentStatus; }
export interface MessageDetailDto extends MessageListItemDto {
  campaignId: UUID;
  invoiceId: UUID | null;
  templateVersionId: UUID;
  resolvedLocale: string | null;
  processingStartedAt: Instant | null;
  providerMessageId: string | null;
  lastErrorCode: string | null;
  lastErrorSummary: string | null;
  attachments: MessageAttachmentDto[];
}
export interface MessageListQuery { status?: string; channel?: string; customerId?: string; page?: number; size?: number; }
