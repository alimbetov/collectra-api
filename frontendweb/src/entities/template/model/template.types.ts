import type { Instant, PageDto, UUID } from '../../../shared/api/contracts';

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
