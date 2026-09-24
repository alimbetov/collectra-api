import { queryOptions } from '@tanstack/react-query';
import { getPublishedTemplateVersions, getTemplateOptions } from './template.api';

export const templateKeys = {
  all: ['templates'] as const,
  options: (channel: string) => [...templateKeys.all, 'options', channel] as const,
  versions: (templateId: string, channel: string) =>
    [...templateKeys.all, 'versions', templateId, channel] as const,
};

export const templateQueries = {
  options: (channel: string) =>
    queryOptions({
      queryKey: templateKeys.options(channel),
      queryFn: () => getTemplateOptions(channel),
      enabled: Boolean(channel),
    }),
  versions: (templateId: string, channel: string) =>
    queryOptions({
      queryKey: templateKeys.versions(templateId, channel),
      queryFn: () => getPublishedTemplateVersions(templateId, channel),
      enabled: Boolean(templateId && channel),
    }),
};
