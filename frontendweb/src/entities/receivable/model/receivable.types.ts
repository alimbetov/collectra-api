import type { DecimalString, Instant, LocalDate, PageDto, UUID } from '../../../shared/api/contracts';

export type PaymentStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export interface InvoiceItemDto {
  id: UUID;
  customerId: UUID;
  customerDisplayName: string;
  contractId?: UUID | null;
  contractNumber?: string | null;
  externalId: string;
  invoiceNumber: string;
  invoiceDate?: LocalDate | null;
  dueDate: LocalDate;
  originalAmount: DecimalString;
  paidAmount: DecimalString;
  outstandingAmount: DecimalString;
  currency: string;
  paymentStatus: PaymentStatus;
  overdue: boolean;
  daysOverdue: number;
}

export interface InvoicePageDto extends PageDto<InvoiceItemDto> {
  businessDate: LocalDate;
}

export interface InvoiceListQuery {
  customerId?: UUID;
  contractId?: UUID;
  paymentStatus?: PaymentStatus;
  currency?: string;
  invoiceNumber?: string;
  externalId?: string;
  search?: string;
  issuedFrom?: LocalDate;
  issuedTo?: LocalDate;
  dueFrom?: LocalDate;
  dueTo?: LocalDate;
  overdue?: boolean;
  amountMin?: DecimalString;
  amountMax?: DecimalString;
  outstandingMin?: DecimalString;
  outstandingMax?: DecimalString;
  page: number;
  size: number;
  sort: string;
}

export interface InvoiceDetailDto {
  id: UUID;
  customerId: UUID;
  contractId?: UUID | null;
  externalId: string;
  invoiceNumber: string;
  invoiceDate?: LocalDate | null;
  dueDate: LocalDate;
  originalAmount: DecimalString;
  paidAmount: DecimalString;
  outstandingAmount: DecimalString;
  currency: string;
  paymentStatus: PaymentStatus;
  overdue: boolean;
  daysOverdue: number;
  businessDate: LocalDate;
  documentFileId?: UUID | null;
  customFields?: unknown | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface InvoiceCreateCommand {
  customerId: UUID;
  contractId?: UUID | null;
  externalId: string;
  invoiceNumber: string;
  invoiceDate?: LocalDate | null;
  dueDate: LocalDate;
  originalAmount: DecimalString;
  currency: string;
  documentFileId?: UUID | null;
  customFields?: unknown | null;
}
