import { apiRequest } from '../../../shared/api/http-client';

export type AnalyticsBucket = 'DAY' | 'WEEK' | 'MONTH';

export interface FinancialMetric {
  currency: string;
  invoiced: string;
  invoiceCount: number;
  payments: string;
  paymentCount: number;
  allocated: string;
  reversedAllocations: string;
  collectionOpened: number;
  collectionClosed: number;
  collectionResolved: number;
  currentSnapshot: null | { outstanding: string; overdueOutstanding: string; openInvoices: number; overdueInvoices: number };
}
export interface FinancialSummary { generatedAt: string; from: string; to: string; currencies: FinancialMetric[]; }
export interface FinancialTimeSeries { generatedAt: string; from: string; to: string; bucket: AnalyticsBucket; items: Array<{ from: string; to: string; currencies: FinancialMetric[] }>; }

export interface CommunicationSummary {
  generatedAt: string; from: string; to: string;
  business: { recipients: number; sent: number; failed: number; skipped: number; retries: number };
  messages: { queued: number; processing: number; retryWait: number; sent: number; failed: number; unknown: number };
  terminalSuccessRate: string; terminalFailureRate: string;
  documents: { created: number; pending: number; ready: number; failed: number; expired: number; accessCount: number; accessedLinks: number };
}
export interface CommunicationChannelReport {
  generatedAt: string; from: string; to: string;
  items: Array<{ channel: string; messageCount: number; recipients: number; sent: number; failed: number; skipped: number; retries: number; retryWaitCurrent: number; unknownCurrent: number; terminalSuccessRate: string }>;
}

function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value) search.set(key, value); });
  const value = search.toString();
  return value ? `?${value}` : '';
}

export function getFinancialSummary(from?: string, to?: string): Promise<FinancialSummary> {
  return apiRequest(`/api/v1/analytics/tenant/summary${query({ from, to })}`);
}
export function getFinancialTimeSeries(from?: string, to?: string, bucket: AnalyticsBucket = 'DAY'): Promise<FinancialTimeSeries> {
  return apiRequest(`/api/v1/analytics/tenant/timeseries${query({ from, to, bucket })}`);
}
export function getCommunicationSummary(from?: string, to?: string): Promise<CommunicationSummary> {
  return apiRequest(`/api/v1/analytics/communication/summary${query({ from, to })}`);
}
export function getCommunicationChannels(from?: string, to?: string): Promise<CommunicationChannelReport> {
  return apiRequest(`/api/v1/analytics/communication/channels${query({ from, to })}`);
}
