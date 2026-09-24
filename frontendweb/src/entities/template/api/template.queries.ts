import { queryOptions } from '@tanstack/react-query';
import type {
  TemplateListParams,
  TemplateVersionListParams,
} from '../model/template.types';
import {
  getBuilderCapabilities,
  getPublishedTemplateVersions,
  getTemplate,
  getTemplateAssets,
  getTemplateFields,
  getTemplateOptions,
  getTemplateVersion,
  getTemplateVersions,
  getTemplates,
} from './template.api';

export const templateKeys = {
  all: ['templates'] as const,
  lists: () => [...templateKeys.all, 'list'] as const,
  list: (params: TemplateListParams) => [...templateKeys.lists(), params] as const,
  detail: (templateId: string) => [...templateKeys.all, 'detail', templateId] as const,
  versionLists: (templateId: string) =>
    [...templateKeys.all, 'versions', templateId] as const,
  versionList: (templateId: string, params: TemplateVersionListParams) =>
    [...templateKeys.versionLists(templateId), params] as const,
  version: (versionId: string) => [...templateKeys.all, 'version', versionId] as const,
  capabilities: () => [...templateKeys.all, 'builder-capabilities'] as const,
  fields: () => [...templateKeys.all, 'fields'] as const,
  fieldPage: (page: number, size: number) => [...templateKeys.fields(), page, size] as const,
  assets: () => [...templateKeys.all, 'assets'] as const,
  assetPage: (page: number, size: number) => [...templateKeys.assets(), page, size] as const,
  options: (channel: string) => [...templateKeys.all, 'options', channel] as const,
  versions: (templateId: string, channel: string) =>
    [...templateKeys.all, 'campaign-versions', templateId, channel] as const,
};

export const templateQueries = {
  list: (params: TemplateListParams) =>
    queryOptions({
      queryKey: templateKeys.list(params),
      queryFn: () => getTemplates(params),
    }),
  detail: (templateId: string) =>
    queryOptions({
      queryKey: templateKeys.detail(templateId),
      queryFn: () => getTemplate(templateId),
      enabled: Boolean(templateId),
    }),
  versionList: (templateId: string, params: TemplateVersionListParams) =>
    queryOptions({
      queryKey: templateKeys.versionList(templateId, params),
      queryFn: () => getTemplateVersions(templateId, params),
      enabled: Boolean(templateId),
    }),
  version: (versionId: string) =>
    queryOptions({
      queryKey: templateKeys.version(versionId),
      queryFn: () => getTemplateVersion(versionId),
      enabled: Boolean(versionId),
    }),
  capabilities: () =>
    queryOptions({
      queryKey: templateKeys.capabilities(),
      queryFn: getBuilderCapabilities,
      staleTime: 30 * 60 * 1000,
    }),
  fields: (page = 0, size = 50) =>
    queryOptions({
      queryKey: templateKeys.fieldPage(page, size),
      queryFn: () => getTemplateFields(page, size),
    }),
  assets: (page = 0, size = 50) =>
    queryOptions({
      queryKey: templateKeys.assetPage(page, size),
      queryFn: () => getTemplateAssets(page, size),
    }),
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
