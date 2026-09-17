import type { CurrencyCode, DecimalString, Instant, LocalDate } from '../../../shared/api/contracts';

export interface CurrencyTotalDto {
  currency: CurrencyCode;
  amount: DecimalString;
}

export interface DashboardSummaryDto {
  asOf: Instant;
  businessDate: LocalDate;
  customers: number;
  activeContracts: number;
  openCollectionCases: number;
  activeCampaigns: number;
  outstandingByCurrency: CurrencyTotalDto[];
}

export interface AgingDto {
  current: DecimalString;
  days1To30: DecimalString;
  days31To60: DecimalString;
  days61To90: DecimalString;
  days90Plus: DecimalString;
}

export interface CurrencyReceivablesDto {
  currency: CurrencyCode;
  outstanding: DecimalString;
  dueToday: number;
  dueSoon: number;
  aging: AgingDto;
}

export interface DashboardReceivablesDto {
  asOf: Instant;
  businessDate: LocalDate;
  currencies: CurrencyReceivablesDto[];
}

export interface DashboardDeliveryDto {
  asOf: Instant;
  businessDate: LocalDate;
  recipients: number;
  sent: number;
  failed: number;
  skipped: number;
  retries: number;
}

export interface DashboardCollectionsDto {
  asOf: Instant;
  businessDate: LocalDate;
  activeCases: number;
  overdueActions: number;
  activePromisesDue: number;
  activePromisesOverdue: number;
  brokenPromises: number;
  openDisputes: number;
}
