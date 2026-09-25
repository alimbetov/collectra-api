import { apiRequest } from '../../../shared/api/http-client';
import type { DownloadUrlDto, FileDto, FileListParams, FilePageDto } from '../model/file.types';

const enc = encodeURIComponent;

export function getFiles(params: FileListParams): Promise<FilePageDto> {
  const query = new URLSearchParams();
  if (params.category) query.set('category', params.category);
  if (params.status) query.set('status', params.status);
  if (params.projectId) query.set('projectId', params.projectId);
  if (params.filename) query.set('filename', params.filename);
  if (params.createdFrom) query.set('createdFrom', params.createdFrom);
  if (params.createdTo) query.set('createdTo', params.createdTo);
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 50));
  query.set('sort', params.sort ?? 'createdAt,desc');
  return apiRequest<FilePageDto>(`/api/v1/files?${query}`);
}

export const getFile = (fileId: string) =>
  apiRequest<FileDto>(`/api/v1/files/${enc(fileId)}`);

export const getFileDownloadUrl = (fileId: string) =>
  apiRequest<DownloadUrlDto>(`/api/v1/files/${enc(fileId)}/download-url`);

export function uploadFile(file: File, category: string, projectId?: string) {
  const body = new FormData();
  body.set('file', file);
  const query = new URLSearchParams({ category });
  if (projectId) query.set('projectId', projectId);
  return apiRequest<FileDto>(`/api/v1/files?${query}`, { method: 'POST', body });
}

export const deleteFile = (fileId: string) =>
  apiRequest<void>(`/api/v1/files/${enc(fileId)}`, { method: 'DELETE' });
