import { apiRequest } from '../../../shared/api/http-client';
import type {
  TemplateOptionPageDto,
  TemplateVersionOptionPageDto,
} from '../model/template.types';

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
