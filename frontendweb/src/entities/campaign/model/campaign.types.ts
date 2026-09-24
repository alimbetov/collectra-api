import type { DecimalString, Instant, PageDto, UUID } from '../../../shared/api/contracts';

export type CampaignStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED' | string;
export type CampaignChannel = 'EMAIL' | 'SMS' | 'TELEGRAM' | 'WHATSAPP';
export type AudienceSelectionType = 'CUSTOMER' | 'RECEIVABLE';

export interface CampaignSelectionDto {
  customerIds: UUID[];
  segmentIds: UUID[];
  daysOverdueFrom: number | null;
  daysOverdueTo: number | null;
  amountFrom: DecimalString | null;
  amountTo: DecimalString | null;
}

export interface CampaignListItemDto {
  id: UUID;
  name: string;
  status: CampaignStatus;
  templateVersionId: UUID;
  channel: CampaignChannel;
  scheduledAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type CampaignPageDto = PageDto<CampaignListItemDto>;

export interface CampaignDetailDto {
  id: UUID;
  name: string;
  status: CampaignStatus;
  channel: CampaignChannel;
  audienceSelectionType: AudienceSelectionType;
  selection: CampaignSelectionDto;
  messageTemplateVersionId: UUID;
  documentTemplateVersionId: UUID | null;
  generatedPdfLink: boolean;
  generatedPdfAttachment: boolean;
  generatedPdfAttachmentRequired: boolean;
  scheduledAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
  revision: number;
}

export interface CampaignListQuery {
  search?: string;
  status?: string;
  channel?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CampaignSaveCommand {
  name: string;
  templateVersionId: UUID;
  channel: CampaignChannel;
  scheduledAt: Instant | null;
  audienceSelectionType: AudienceSelectionType;
  selection: CampaignSelectionDto;
  documentTemplateVersionId: UUID | null;
  generatedPdfLink: boolean;
}

export interface CampaignUpdateCommand extends CampaignSaveCommand {
  revision: number;
}

export interface ValidationIssueDto {
  code: string;
  message: string;
}

export interface CampaignValidationDto {
  valid: boolean;
  errors: ValidationIssueDto[];
  warnings: ValidationIssueDto[];
}

export interface CampaignPreviewDto {
  customerId: UUID;
  invoiceId: UUID | null;
  channel: CampaignChannel;
  destination: string;
  requestedLocale: string;
  resolvedLocale: string;
  messageTemplateVersionId: UUID;
  subject: string | null;
  body: string;
  documentTemplateVersionId: UUID | null;
  documentUrlPreview: string | null;
  warnings: ValidationIssueDto[];
}

export interface PrepareRunDto {
  runId: UUID;
  recipients: number;
}

export interface CampaignRunDto {
  id: UUID;
  campaignId: UUID;
  status: string;
  recipientCount: number;
  sentCount: number;
  failedCount: number;
  skippedCount: number;
  retryCount: number;
  pendingCount: number;
  preparedAt: Instant | null;
  startedAt: Instant | null;
  completedAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type CampaignRunPageDto = PageDto<CampaignRunDto>;

export interface CampaignRecipientDto {
  id: UUID;
  customerId: UUID;
  invoiceId: UUID | null;
  channel: CampaignChannel;
  destination: string;
  locale: string | null;
  status: string;
  skipReason: string | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type CampaignRecipientPageDto = PageDto<CampaignRecipientDto>;
