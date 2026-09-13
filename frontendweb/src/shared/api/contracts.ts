export type UUID = string;
export type Instant = string;
export type LocalDate = string;
export type CurrencyCode = string;
export type Decimal = number;

export interface PageDto<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface SliceDto<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface ProblemDetailDto {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  correlationId?: string;
  errors?: Record<string, string | string[]>;
  batchId?: UUID;
}
