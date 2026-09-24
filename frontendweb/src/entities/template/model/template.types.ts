import type { Instant, PageDto, UUID } from '../../../shared/api/contracts';

export type TemplateChannel = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'TELEGRAM' | 'PDF';
export type TemplateVersionStatus = 'DRAFT' | 'VALIDATED' | 'PUBLISHED' | 'ARCHIVED';

export interface TemplateOptionDto {
  id: UUID;
  code: string;
  name: string;
  documentType: string;
  status: string;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface TemplateVersionOptionDto {
  id: UUID;
  templateId: UUID;
  templateVersion: number;
  locale: string;
  channel: string;
  subject: string | null;
  status: string;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type TemplateOptionPageDto = PageDto<TemplateOptionDto>;
export type TemplateVersionOptionPageDto = PageDto<TemplateVersionOptionDto>;

export interface TemplateListItemDto {
  id: UUID;
  code: string;
  name: string;
  documentType: string;
  status: string;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface TemplateDetailDto {
  id: UUID;
  code: string;
  name: string;
  documentType: string;
  status: string;
  createdAt: Instant;
  updatedAt: Instant;
  revision: number;
}

export interface TemplateListParams {
  search?: string;
  channel?: TemplateChannel;
  status?: string;
  locale?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TemplateVersionListItemDto {
  id: UUID;
  templateId: UUID;
  templateVersion: number;
  locale: string;
  channel: TemplateChannel;
  subject: string | null;
  status: TemplateVersionStatus;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface TemplateVersionListParams {
  channel?: TemplateChannel;
  status?: TemplateVersionStatus;
  locale?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
}

export interface BuilderVersionDto {
  id: UUID;
  templateId: UUID;
  version: number;
  templateVersion: number;
  locale: string;
  channel: TemplateChannel;
  subject: string | null;
  builderJson: BuilderDocumentDto | null;
  content: string;
  stylesheet: string | null;
  status: TemplateVersionStatus;
  createdAt: Instant;
  updatedAt: Instant;
  revision: number;
}

export interface BuilderDocumentDto {
  version: '1.0';
  blocks: BuilderBlockDto[];
}

export type BuilderBlockDto =
  | { type: 'header' | 'footer' | 'row' | 'column'; children?: BuilderBlockDto[] }
  | {
      type: 'richText';
      props: {
        content: Array<
          | { type: 'text'; value: string }
          | { type: 'placeholder'; key: string }
        >;
      };
    }
  | {
      type: 'itemsTable';
      props: {
        dataSource: 'items';
        columns: Array<{ key: string; label: string }>;
      };
    }
  | { type: 'image'; props: { assetKey: string; alt?: string } }
  | { type: 'spacer'; props: { heightPx: number } };

export interface BuilderCapabilitiesDto {
  channels: TemplateChannel[];
  eachSyntax: string;
  assetSyntax: string;
  builderSchemaVersion: string;
}

export interface TemplateFieldDto {
  id: UUID;
  key: string;
  label: string;
  dataType: string;
  category: string;
  collection: boolean;
  required: boolean;
  system: boolean;
  description: string | null;
  exampleValue: string | null;
  validationRules: unknown;
}

export interface TemplateAssetDto {
  id: UUID;
  key: string;
  fileId: UUID;
  altText: string | null;
  placeholder: string;
}

export interface BuilderPageDto<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TemplateValidationIssueDto {
  code: string;
  path: string;
  message: string;
}

export interface TemplateValidationResultDto {
  valid: boolean;
  errors: TemplateValidationIssueDto[];
  warnings: TemplateValidationIssueDto[];
}

export interface TemplatePreviewDto {
  channel: TemplateChannel;
  subject: string | null;
  content: string;
}

export interface CreateTemplateCommand {
  code: string;
  name: string;
  documentType: string;
}

export interface RenameTemplateCommand {
  name: string;
  revision: number;
}

export interface CreateVersionCommand {
  channel: TemplateChannel;
  locale: string;
  subject?: string | null;
  contentHtml: string;
  stylesheet?: string | null;
}

export interface BuilderDraftCommand {
  channel: TemplateChannel;
  locale: string;
  subject?: string | null;
  builderJson: BuilderDocumentDto;
  stylesheet?: string | null;
  revision?: number;
}

export interface TextDraftCommand {
  channel: TemplateChannel;
  locale: string;
  subject?: string | null;
  content: string;
  stylesheet?: string | null;
  revision?: number;
}
