import { apiBlobRequest, apiRequest } from '../../../shared/api/http-client';
import type {
  BuilderCapabilitiesDto,
  BuilderDraftCommand,
  BuilderPageDto,
  BuilderVersionDto,
  CreateTemplateCommand,
  CreateVersionCommand,
  RenameTemplateCommand,
  TemplateAssetDto,
  TemplateDetailDto,
  TemplateFieldDto,
  TemplateListItemDto,
  TemplateListParams,
  TemplateOptionPageDto,
  TemplatePreviewDto,
  TemplateValidationResultDto,
  TemplateVersionListItemDto,
  TemplateVersionListParams,
  TemplateVersionOptionPageDto,
  TextDraftCommand,
} from '../model/template.types';
import type { PageDto } from '../../../shared/api/contracts';

function appendIf(params: URLSearchParams, key: string, value?: string) {
  if (value) params.set(key, value);
}

function templateListQuery(input: TemplateListParams) {
  const params = new URLSearchParams();
  appendIf(params, 'search', input.search);
  appendIf(params, 'channel', input.channel);
  appendIf(params, 'status', input.status);
  appendIf(params, 'locale', input.locale);
  appendIf(params, 'createdFrom', input.createdFrom);
  appendIf(params, 'createdTo', input.createdTo);
  params.set('page', String(input.page ?? 0));
  params.set('size', String(input.size ?? 50));
  params.set('sort', input.sort ?? 'createdAt,desc');
  return params.toString();
}

function versionListQuery(input: TemplateVersionListParams) {
  const params = new URLSearchParams();
  appendIf(params, 'channel', input.channel);
  appendIf(params, 'status', input.status);
  appendIf(params, 'locale', input.locale);
  appendIf(params, 'createdFrom', input.createdFrom);
  appendIf(params, 'createdTo', input.createdTo);
  params.set('page', String(input.page ?? 0));
  params.set('size', String(input.size ?? 50));
  return params.toString();
}

export const getTemplates = (params: TemplateListParams) =>
  apiRequest<PageDto<TemplateListItemDto>>(`/api/v1/templates?${templateListQuery(params)}`);

export const createTemplate = (command: CreateTemplateCommand) =>
  apiRequest<TemplateDetailDto>('/api/v1/templates', {
    method: 'POST',
    body: command,
  });

export const getTemplate = (templateId: string) =>
  apiRequest<TemplateDetailDto>(`/api/v1/templates/${encodeURIComponent(templateId)}`);

export const renameTemplate = (templateId: string, command: RenameTemplateCommand) =>
  apiRequest<TemplateDetailDto>(`/api/v1/templates/${encodeURIComponent(templateId)}`, {
    method: 'PUT',
    body: command,
  });

export const archiveTemplate = (templateId: string, revision: number) =>
  apiRequest<void>(
    `/api/v1/templates/${encodeURIComponent(templateId)}?revision=${revision}`,
    { method: 'DELETE' },
  );

export const getTemplateVersions = (templateId: string, params: TemplateVersionListParams) =>
  apiRequest<PageDto<TemplateVersionListItemDto>>(
    `/api/v1/templates/${encodeURIComponent(templateId)}/versions?${versionListQuery(params)}`,
  );

export const createTemplateVersion = (templateId: string, command: CreateVersionCommand) =>
  apiRequest<BuilderVersionDto>(
    `/api/v1/templates/${encodeURIComponent(templateId)}/versions`,
    {
      method: 'POST',
      body: command,
    },
  );

export const getBuilderCapabilities = () =>
  apiRequest<BuilderCapabilitiesDto>('/api/v1/template-builder/capabilities');

export const getTemplateFields = (page = 0, size = 50) =>
  apiRequest<BuilderPageDto<TemplateFieldDto>>(
    `/api/v1/template-builder/catalog/fields?page=${page}&size=${size}`,
  );

export const getTemplateAssets = (page = 0, size = 50) =>
  apiRequest<BuilderPageDto<TemplateAssetDto>>(
    `/api/v1/template-builder/catalog/assets?page=${page}&size=${size}`,
  );

export const getTemplateVersion = (versionId: string) =>
  apiRequest<BuilderVersionDto>(
    `/api/v1/template-builder/versions/${encodeURIComponent(versionId)}`,
  );

export const createBuilderVersion = (templateId: string, command: BuilderDraftCommand) =>
  apiRequest<BuilderVersionDto>(
    `/api/v1/template-builder/templates/${encodeURIComponent(templateId)}/versions/builder`,
    { method: 'POST', body: command },
  );

export const updateBuilderVersion = (versionId: string, command: BuilderDraftCommand) =>
  apiRequest<BuilderVersionDto>(
    `/api/v1/template-builder/versions/${encodeURIComponent(versionId)}/builder`,
    { method: 'PUT', body: command },
  );

export const updateTextVersion = (versionId: string, command: TextDraftCommand) =>
  apiRequest<BuilderVersionDto>(
    `/api/v1/template-builder/versions/${encodeURIComponent(versionId)}`,
    { method: 'PUT', body: command },
  );

export const validateBuilderDraft = (command: BuilderDraftCommand) =>
  apiRequest<TemplateValidationResultDto>('/api/v1/template-builder/documents/validate', {
    method: 'POST',
    body: command,
  });

export const previewBuilderDraft = (command: BuilderDraftCommand, payload: unknown) =>
  apiRequest<TemplatePreviewDto>('/api/v1/template-builder/documents/preview', {
    method: 'POST',
    body: { draft: command, payload },
  });

export const validateTextDraft = (command: TextDraftCommand) =>
  apiRequest<TemplateValidationResultDto>('/api/v1/template-builder/validate', {
    method: 'POST',
    body: command,
  });

export const previewTextDraft = (command: TextDraftCommand, payload: unknown) =>
  apiRequest<TemplatePreviewDto>('/api/v1/template-builder/preview', {
    method: 'POST',
    body: { draft: command, payload },
  });

export const validateSavedVersion = (versionId: string, revision: number) =>
  apiRequest<TemplateValidationResultDto>(
    `/api/v1/template-builder/versions/${encodeURIComponent(versionId)}/validate?revision=${revision}`,
    { method: 'POST' },
  );

export const transitionTemplateVersion = (
  versionId: string,
  action: 'publish' | 'reopen' | 'archive',
  revision: number,
) =>
  apiRequest<BuilderVersionDto>(
    `/api/v1/templates/versions/${encodeURIComponent(versionId)}/${action}?revision=${revision}`,
    { method: 'POST' },
  );

export const previewPdf = (command: BuilderDraftCommand, payload: unknown) =>
  apiBlobRequest('/api/v1/template-builder/documents/preview-pdf', {
    method: 'POST',
    headers: { Accept: 'application/pdf' },
    body: { draft: command, payload },
  });

export const getTemplateOptions = (channel: string) => {
  const params = new URLSearchParams({
    channel,
    page: '0',
    size: '100',
    sort: 'name,asc',
  });
  return apiRequest<TemplateOptionPageDto>(`/api/v1/templates?${params}`);
};

export const getPublishedTemplateVersions = (templateId: string, channel: string) => {
  const params = new URLSearchParams({
    channel,
    status: 'PUBLISHED',
    page: '0',
    size: '100',
  });
  return apiRequest<TemplateVersionOptionPageDto>(
    `/api/v1/templates/${encodeURIComponent(templateId)}/versions?${params}`,
  );
};
