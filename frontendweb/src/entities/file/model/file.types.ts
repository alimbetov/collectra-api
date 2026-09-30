export const FILE_CATEGORIES = ['IMPORT_SOURCE', 'REPORT', 'EXPORT', 'ASSET', 'TEMP'] as const;
export type FileCategory = (typeof FILE_CATEGORIES)[number];
export type FileStatus = 'UPLOADING' | 'READY' | 'FAILED' | 'DELETE_PENDING' | 'DELETED';

export interface FileDto {
  fileId: string;
  projectId: string | null;
  category: FileCategory;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number | null;
  status: FileStatus;
  createdAt: string;
  expiresAt: string | null;
  deletedAt: string | null;
}

export interface FilePageDto {
  items: FileDto[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface FileListParams {
  category?: FileCategory;
  status?: FileStatus;
  projectId?: string;
  filename?: string;
  createdFrom?: string;
  createdTo?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface DownloadUrlDto {
  fileId: string;
  url: string;
  expiresInSeconds: number;
}
