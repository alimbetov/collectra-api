import { apiRequest } from '../../../shared/api/http-client';

export interface IngestionBatchSummary {
  id: string; sourceCode: string; status: string; idempotencyKey: string; requestId: string;
  receivedAt: string; completedAt: string | null; recordCount: number; createdCount: number;
  reusedCount: number; conflictCount: number; failedCount: number;
}
export interface PageDto<T> { content: T[]; number: number; size: number; totalElements: number; totalPages: number; }
export interface CommunicationOperations {
  generatedAt: string; from: string; to: string; queued: number; processing: number; retryWait: number;
  unknown: number; failed: number; stuckProcessing: number; dueRetry: number;
  oldestQueuedAgeSeconds: number | null; oldestStuckAgeSeconds: number | null; oldestDueRetryAgeSeconds: number | null;
}
export interface GenerationJobDiagnostic {
  id: string; status: string; currentStep: string | null; attemptCount: number; errorCode: string | null; errorMessage: string | null;
}

export function getIngestionBatches(status?: string): Promise<PageDto<IngestionBatchSummary>> {
  const q = status ? `?status=${encodeURIComponent(status)}&page=0&size=50` : '?page=0&size=50';
  return apiRequest(`/api/v1/integration/ingestion-batches${q}`);
}
export function getCommunicationOperations(): Promise<CommunicationOperations> {
  return apiRequest('/api/v1/analytics/communication/operations');
}
export function getGenerationJob(jobId: string): Promise<GenerationJobDiagnostic> {
  return apiRequest(`/api/v1/documents/generation-jobs/${encodeURIComponent(jobId)}`);
}
