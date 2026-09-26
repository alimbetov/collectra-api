import { queryOptions } from '@tanstack/react-query';
import { getFile, getFiles } from './file.api';
import type { FileListParams } from '../model/file.types';

export const fileKeys = {
  all: ['files'] as const,
  list: (params: FileListParams) => ['files', 'list', params] as const,
  detail: (fileId: string) => ['files', 'detail', fileId] as const,
};

export const fileQueries = {
  list: (params: FileListParams) =>
    queryOptions({ queryKey: fileKeys.list(params), queryFn: () => getFiles(params) }),
  detail: (fileId: string) =>
    queryOptions({ queryKey: fileKeys.detail(fileId), queryFn: () => getFile(fileId), enabled: Boolean(fileId) }),
};
