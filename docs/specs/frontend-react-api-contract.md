# FrontendWeb — React API Contract

Status: BASELINE AUDITED / FW3–FW12 CORRECTIONS REQUIRED

Purpose: зафиксировать TypeScript DTO, API client rules, TanStack Query keys, mutation invalidation и auth/session behavior на базе фактического backend API текущей ветки.

This document is downstream from:

- `frontend-ui-information-architecture.md`
- `frontend-user-processes.md`
- `frontend-screen-api-matrix.md`

The backend remains authoritative for business state. React is a projection/client orchestration layer, not a second domain engine.

FW3–FW12 review and required contract migrations are recorded in
[`frontendweb-fw03-fw12-review.md`](frontendweb-fw03-fw12-review.md). A DTO shown here is
not implementation-ready when that review marks its backend projection as gated.

## 1. Transport primitives

```ts
export type UUID = string;
export type Instant = string;   // ISO-8601 UTC/offset timestamp
export type LocalDate = string; // YYYY-MM-DD
export type CurrencyCode = string; // ISO-4217, e.g. KZT

// CURRENT wire format only; blocked for financial UI pending the money contract ADR.
// Prefer canonical decimal strings with documented precision/scale.
export type Decimal = number;
```

Rules:

- React MUST NOT derive `paidAmount`, `outstandingAmount`, `paymentStatus`, `daysOverdue`, allocation validity or collection eligibility.
- All money arithmetic that changes business truth stays on backend.
- Decimal-string transport is now a Definition of Ready decision for FW3/FW5/FW6 and is
  an explicit reviewed OpenAPI migration, not a silent TypeScript reinterpretation.

## 2. Common transport contracts

```ts
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
```

Feature adapters may normalize `items` and `content` into an internal UI page model, but transport DTOs must reflect the backend shape exactly.

Maximum page size for the current list APIs is `200` unless an endpoint defines a smaller bound.

## 3. Authentication and session

### DTOs

```ts
export interface RegisterTenantRequest {
  slug: string;
  companyName: string;
  email: string;
  password: string;
}

export interface LoginBySlugRequest {
  tenantSlug: string;
  email: string;
  password: string;
}

export interface LegacyLoginRequest {
  tenantId: UUID;
  email: string;
  password: string;
}

export interface TokenRequest {
  refreshToken: string;
}

export interface AuthTokensDto {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer' | string;
  expiresIn: number;
}

export interface MeDto {
  id: UUID;
  email: string;
  displayName: string | null;
  locale: string | null;
  timezone: string | null;
  membershipId: UUID;
  membershipStatus: string;
  roles: string[];
  permissions: string[];
}

export interface UpdateMeRequest {
  displayName: string;
  locale?: string | null;
  timezone?: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface SessionDto {
  id: UUID;
  createdAt: Instant;
  expiresAt: Instant;
  lastUsedAt: Instant | null;
  revokedAt: Instant | null;
  userAgent: string | null;
  sourceIp: string | null;
}
```

### API

```ts
POST   /api/v1/auth/tenants/register
POST   /api/v1/auth/login/by-slug
POST   /api/v1/auth/login              // compatibility only
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/logout-all
GET    /api/v1/identity/me
PATCH  /api/v1/identity/me
POST   /api/v1/identity/me/change-password
GET    /api/v1/identity/me/sessions
DELETE /api/v1/identity/me/sessions/{id}
```

Frontend MUST use `login/by-slug`; ordinary users do not enter tenant UUID.

### Auth client behavior

- keep exactly one in-flight refresh request (`single-flight refresh`);
- on first `401` from an authenticated request, refresh once and replay the original request once;
- never refresh recursively for refresh/login/logout endpoints;
- failed refresh clears client auth state and redirects to login;
- `403` is authorization failure and MUST NOT trigger refresh;
- logout clears query cache for tenant-bound data;
- bootstrap after authentication is `GET /api/v1/identity/me`.

The frontend must not infer permissions from role names when a permission code is available. `MeDto.permissions` is the primary UI capability source; backend authorization remains authoritative.

## 4. Dashboard

```ts
export interface CurrencyTotalDto {
  currency: CurrencyCode;
  amount: Decimal;
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
  current: Decimal;
  days1To30: Decimal;
  days31To60: Decimal;
  days61To90: Decimal;
  days90Plus: Decimal;
}

export interface CurrencyReceivablesDto {
  currency: CurrencyCode;
  outstanding: Decimal;
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
```

API:

```ts
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/receivables
GET /api/v1/dashboard/delivery
GET /api/v1/dashboard/collections
```

## 5. Customers and segments

```ts
export type CustomerType = string;
export type CustomerStatus = string;

export interface SegmentSummaryDto {
  id: UUID;
  code: string;
  name: string;
}

export interface CustomerListItemDto {
  id: UUID;
  externalId: string;
  customerType: CustomerType;
  displayName: string;
  status: CustomerStatus;
  managerUserId: UUID | null;
  preferredLocale: string | null;
  timezone: string | null;
  segmentIds: UUID[];
  primaryEmail: string | null;
  primaryPhone: string | null;
  managerDisplayName: string | null;
  segments: SegmentSummaryDto[];
  createdAt: Instant;
  updatedAt: Instant;
}

export type CustomerPageDto = PageDto<CustomerListItemDto>;

export interface SegmentDto {
  id: UUID;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type SegmentPageDto = PageDto<SegmentDto>;

export interface CustomerListQuery {
  search?: string;
  status?: CustomerStatus;
  customerType?: CustomerType;
  managerId?: UUID;
  segmentId?: UUID;
  externalId?: string;
  email?: string;
  phone?: string;
  createdFrom?: Instant;
  createdTo?: Instant;
  page?: number;
  size?: number;
  sort?: string;
}
```

API family:

```ts
GET    /api/v1/customers
GET    /api/v1/customers/{id}
POST   /api/v1/customers
PUT    /api/v1/customers/{id}
PATCH  /api/v1/customers/{id}/status
...    /api/v1/customers/{id}/emails
...    /api/v1/customers/{id}/phones
...    /api/v1/customers/{id}/segments/{segmentId}
GET    /api/v1/customer-segments
GET    /api/v1/customer-segments/{segmentId}
POST   /api/v1/customer-segments
PATCH  /api/v1/customer-segments/{segmentId}
```

Customer list MUST use the enriched read projection and MUST NOT issue per-row contact/manager/segment lookups.

## 6. Contracts

```ts
export type ContractStatus = string;

export interface ContractListItemDto {
  id: UUID;
  customerId: UUID;
  externalId: string;
  contractNumber: string;
  status: ContractStatus;
  validFrom: LocalDate;
  validTo: LocalDate | null;
  renewalDate: LocalDate | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export type ContractPageDto = PageDto<ContractListItemDto>;

export interface ContractListQuery {
  search?: string;
  customerId?: UUID;
  status?: ContractStatus;
  externalId?: string;
  validFrom?: LocalDate;
  validTo?: LocalDate;
  createdFrom?: Instant;
  createdTo?: Instant;
  page?: number;
  size?: number;
  sort?: string;
}
```

API:

```ts
GET  /api/v1/contracts
GET  /api/v1/contracts/{id}
POST /api/v1/contracts
PUT  /api/v1/contracts/{id}
POST /api/v1/contracts/{id}/suspend
POST /api/v1/contracts/{id}/activate
POST /api/v1/contracts/{id}/close
POST /api/v1/contracts/{id}/cancel
```

Lifecycle actions are commands. React MUST NOT expose arbitrary status editing.

## 7. Receivables and payments

```ts
export type PaymentStatus = string;

export interface InvoiceListItemDto {
  id: UUID;
  customerId: UUID;
  contractId: UUID | null;
  externalId: string;
  invoiceNumber: string;
  invoiceDate: LocalDate;
  dueDate: LocalDate;
  originalAmount: Decimal;
  paidAmount: Decimal;
  outstandingAmount: Decimal;
  currency: CurrencyCode;
  paymentStatus: PaymentStatus;
  overdue: boolean;
  daysOverdue: number;
}

export interface InvoicePageDto extends PageDto<InvoiceListItemDto> {
  businessDate: LocalDate;
}

export interface InvoiceListQuery {
  customerId?: UUID;
  contractId?: UUID;
  paymentStatus?: PaymentStatus;
  currency?: CurrencyCode;
  invoiceNumber?: string;
  externalId?: string;
  search?: string;
  issuedFrom?: LocalDate;
  issuedTo?: LocalDate;
  dueFrom?: LocalDate;
  dueTo?: LocalDate;
  overdue?: boolean;
  amountMin?: Decimal;
  amountMax?: Decimal;
  outstandingMin?: Decimal;
  outstandingMax?: Decimal;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PaymentListItemDto {
  id: UUID;
  customerId: UUID;
  externalId: string;
  paymentDate: LocalDate;
  amount: Decimal;
  currency: CurrencyCode;
  paymentReference: string | null;
  source: string | null;
}

export type PaymentPageDto = PageDto<PaymentListItemDto>;

export interface AllocationCreateRequest {
  commandId: UUID;
  invoiceId: UUID;
  amount: Decimal;
}

export interface AllocationReverseRequest {
  version: number;
  reason: string;
}
```

API:

```ts
GET  /api/v1/invoices
GET  /api/v1/invoices/{id}
POST /api/v1/invoices
GET  /api/v1/invoices/{id}/allocations
GET  /api/v1/payments
GET  /api/v1/payments/{id}
POST /api/v1/payments
GET  /api/v1/payments/{id}/allocations
POST /api/v1/payments/{id}/allocations
POST /api/v1/payments/{paymentId}/allocations/{allocationId}/reverse
```

For a single user allocation intent, generate one `commandId` and reuse that same value when replaying the uncertain request.

## 8. Collections

```ts
export type CollectionCaseStatus = string;
export type CollectionPriority = string;

export interface CollectionCaseListItemDto {
  id: UUID;
  customerId: UUID;
  customerDisplayName: string | null;
  invoiceId: UUID;
  invoiceNumber: string | null;
  status: CollectionCaseStatus;
  priority: CollectionPriority;
  assignedTo: UUID | null;
  assigneeDisplayName: string | null;
  currency: CurrencyCode | null;
  outstandingAmount: Decimal | null;
  paymentStatus: string | null;
  nextActionType: string | null;
  nextActionDueAt: Instant | null;
  nextActionOverdue: boolean;
  openedAt: Instant;
  closedAt: Instant | null;
  closeReason: string | null;
  version: number;
}

export type CollectionCasePageDto = PageDto<CollectionCaseListItemDto>;
```

API family:

```ts
GET  /api/v1/collection-cases
GET  /api/v1/collection-cases/{caseId}
POST /api/v1/collection-cases
PUT  /api/v1/collection-cases/{caseId}
POST /api/v1/collection-cases/{caseId}/start
POST /api/v1/collection-cases/{caseId}/hold
POST /api/v1/collection-cases/{caseId}/close
...  /promises
...  /disputes
...  /actions
GET  /api/v1/collection-cases/{caseId}/timeline
```

The timeline returned by backend is authoritative. React MUST NOT synthesize workflow history locally.

## 9. Campaigns

```ts
export interface CampaignSelectionDto {
  customerIds: UUID[];
  segmentIds: UUID[];
  daysOverdueFrom?: number | null;
  daysOverdueTo?: number | null;
  amountFrom?: Decimal | null;
  amountTo?: Decimal | null;
}

export interface CampaignListItemDto {
  id: UUID;
  name: string;
  status: string;
  templateVersionId: UUID;
  channel: string;
  scheduledAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface CampaignRunDto {
  id: UUID;
  campaignId: UUID;
  status: string;
  recipientCount: number;
  sentCount: number;
  failedCount: number;
  skippedCount: number;
  retryCount: number;
  pendingCount: number;
  preparedAt: Instant | null;
  startedAt: Instant | null;
  completedAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}

export interface CampaignRecipientDto {
  id: UUID;
  customerId: UUID;
  invoiceId: UUID | null;
  channel: string;
  destination: string; // already masked by backend
  locale: string | null;
  status: string;
  skipReason: string | null;
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}
```

API:

```ts
GET  /api/v1/campaigns
GET  /api/v1/campaigns/{campaignId}
POST /api/v1/campaigns
POST /api/v1/campaigns/{campaignId}/activate
POST /api/v1/campaigns/{campaignId}/runs
GET  /api/v1/campaigns/{campaignId}/runs
GET  /api/v1/campaigns/{campaignId}/runs/{runId}/recipients
POST /api/v1/campaigns/runs/{runId}/eligibility-recheck
```

Provider-specific state never crosses into React contracts.

## 10. Messages / delivery monitoring

```ts
export interface MessageListItemDto {
  id: UUID;
  campaignRunId: UUID;
  customerId: UUID;
  channel: string;
  maskedDestination: string;
  status: string;
  attemptCount: number;
  nextRetryAt: Instant | null;
  sentAt: Instant | null;
  createdAt: Instant;
}

export interface MessageSliceDto extends SliceDto<MessageListItemDto> {}

export interface MessageAttachmentDto {
  id: UUID;
  filename: string;
  contentType: string;
  size: number | null;
  required: boolean;
  status: string;
}

export interface MessageDetailDto {
  id: UUID;
  campaignId: UUID;
  campaignRunId: UUID;
  customerId: UUID;
  invoiceId: UUID | null;
  templateVersionId: UUID;
  channel: string;
  maskedDestination: string;
  resolvedLocale: string | null;
  status: string;
  attemptCount: number;
  nextRetryAt: Instant | null;
  sentAt: Instant | null;
  processingStartedAt: Instant | null;
  providerMessageId: string | null;
  lastErrorCode: string | null;
  lastErrorSummary: string | null;
  createdAt: Instant;
  attachments: MessageAttachmentDto[];
}
```

API:

```ts
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages
GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages/{messageId}
```

Message list response is a slice and intentionally has no `totalElements`.

## 11. Imports

```ts
export interface ImportDocumentResultDto {
  order: number;
  documentKey: string;
  generationJobId: UUID;
}

export interface ImportFailureDto {
  code: string;
  message: string;
  failedAt: Instant;
}

export interface ImportBatchResultDto {
  batchId: UUID;
  status: string;
  documentCount: number;
  replayed: boolean;
  documents: ImportDocumentResultDto[];
  failure: ImportFailureDto | null;
}
```

Execution APIs:

```ts
POST /api/v1/import-batches          // multipart
POST /api/v1/import-batches/json
POST /api/v1/import-batches/xml
GET  /api/v1/import-batches/{batchId}
```

Every create intent uses a stable `Idempotency-Key`. Re-submit the same key only for the same logical request.

Configuration APIs remain grouped under:

```ts
/api/v1/source-schemas
/api/v1/mapping-profiles
```

Frontend modules for configuration and execution must be separate.

## 12. Templates / builder

The frontend treats template management and template builder as separate feature layers.

Management:

```ts
/api/v1/templates
/api/v1/templates/{id}/versions
/api/v1/templates/versions/{id}/validate
/api/v1/templates/versions/{id}/preview
/api/v1/templates/versions/{id}/publish
/api/v1/templates/versions/{id}/reopen
/api/v1/templates/versions/{id}/archive
```

Builder:

```ts
GET  /api/v1/template-builder/catalog
POST /api/v1/template-builder/validate
POST /api/v1/template-builder/preview
...  /api/v1/template-builder/documents
...  /api/v1/template-builder/templates/{id}/versions
...  /api/v1/template-builder/versions/{id}
...  /api/v1/template-builder/assets
```

Builder DTOs should be colocated under `features/templates/builder/contracts.ts`; do not mix them into generic template list contracts.

## 13. Files

API:

```ts
POST   /api/v1/files
GET    /api/v1/files/{fileId}
GET    /api/v1/files/{fileId}/content
GET    /api/v1/files/{fileId}/download-url
DELETE /api/v1/files/{fileId}
```

React never constructs RustFS/object-storage URLs. Download/open behavior uses only backend-returned content or download URL.

## 14. Tenant administration

```ts
export interface TenantMemberDto {
  id: UUID; // current backend JSON; boundary adapter normalizes this to membershipId
  userId: UUID;
  email: string;
  displayName: string | null;
  status: string;
}
```

API:

```ts
GET   /api/v1/identity/users
GET   /api/v1/identity/memberships/{id}/roles
PUT   /api/v1/identity/memberships/{id}/roles
PATCH /api/v1/identity/memberships/{id}/status
...   /api/v1/identity/memberships/{id}/sessions
...   /api/v1/identity/invitations
...   /api/v1/identity/roles
GET   /api/v1/identity/permissions
```

Role picker loads current roles only when a member is opened; the member list does not fan out one role request per row.
FW12 backend hardening migrates the public field to explicit `membershipId` and pages
members/invitations. Features use only the normalized name.

## 15. HTTP client boundary

Recommended module:

```text
shared/api/http-client.ts
shared/api/problem-detail.ts
shared/auth/session.ts
```

Required behavior:

```ts
request(config)
  -> attach access token when present
  -> execute
  -> if 401 and refresh-eligible
       -> await single shared refreshPromise
       -> update tokens
       -> replay once
  -> map non-2xx body to ProblemDetailDto
```

Never log access tokens, refresh tokens, authorization headers, raw message body, unmasked recipient addresses or attachment content.

## 16. TanStack Query key factory

```ts
export const qk = {
  me: () => ['me'] as const,
  sessions: () => ['me', 'sessions'] as const,
  dashboard: (part: string) => ['dashboard', part] as const,
  customers: (filters: unknown) => ['customers', filters] as const,
  customer: (id: UUID) => ['customer', id] as const,
  segments: (filters: unknown) => ['segments', filters] as const,
  contracts: (filters: unknown) => ['contracts', filters] as const,
  contract: (id: UUID) => ['contract', id] as const,
  invoices: (filters: unknown) => ['invoices', filters] as const,
  invoice: (id: UUID) => ['invoice', id] as const,
  payments: (filters: unknown) => ['payments', filters] as const,
  payment: (id: UUID) => ['payment', id] as const,
  collections: (filters: unknown) => ['collection-cases', filters] as const,
  collection: (id: UUID) => ['collection-case', id] as const,
  campaigns: (filters: unknown) => ['campaigns', filters] as const,
  campaign: (id: UUID) => ['campaign', id] as const,
  campaignRuns: (campaignId: UUID, filters?: unknown) => ['campaign-runs', campaignId, filters] as const,
  campaignRun: (campaignId: UUID, runId: UUID) => ['campaign-run', campaignId, runId] as const,
  recipients: (campaignId: UUID, runId: UUID, filters?: unknown) => ['campaign-recipients', campaignId, runId, filters] as const,
  messages: (campaignId: UUID, runId: UUID, filters?: unknown) => ['messages', campaignId, runId, filters] as const,
  message: (campaignId: UUID, runId: UUID, messageId: UUID) => ['message', campaignId, runId, messageId] as const,
  importBatch: (id: UUID) => ['import-batch', id] as const,
  imports: (filters: unknown) => ['imports', filters] as const,
  importErrors: (id: UUID, filters: unknown) => ['import-errors', id, filters] as const,
  templates: (filters: unknown) => ['templates', filters] as const,
  template: (id: UUID) => ['template', id] as const,
  templateVersions: (id: UUID, filters: unknown) => ['template-versions', id, filters] as const,
  files: (filters: unknown) => ['files', filters] as const,
  file: (id: UUID) => ['file', id] as const,
  tenantMembers: (filters: unknown) => ['tenant-members', filters] as const,
  invitations: (filters: unknown) => ['tenant-invitations', filters] as const,
  memberRoles: (membershipId: UUID) => ['tenant-member-roles', membershipId] as const,
};
```

Filter objects used in keys must be canonical/stable objects; omit `undefined` values before constructing the key.

## 17. Mutation invalidation graph

Minimum invalidation rules:

- customer create/update/status/contact/segment mutation -> customer detail + customer lists; contact/segment mutation also invalidates affected segment/customer projections;
- contract mutation -> contract detail/list + customer-related contract views;
- invoice create -> invoice lists + customer views + dashboard receivables/summary;
- payment create -> payment lists + dashboard receivables/summary;
- allocation/reversal -> payment detail + payment allocations + invoice detail + invoice allocations + invoice/payment lists + dashboard receivables + related collection case queries;
- collection workflow/promise/dispute/action mutation -> case detail + case list + timeline + dashboard collections; if financial eligibility can change, also invalidate affected invoice query;
- template mutation/publish -> template list/detail/version queries;
- campaign create/activate/prepare/recheck -> campaign detail/list + runs + recipients + messages as applicable + dashboard delivery/summary;
- import create -> its batch query; when import creates business data, refresh relevant customers/invoices/contracts according to the import result flow;
- member role/status mutation -> tenant members/memberRoles + `me` only when the current user's own membership changed.

Do not call `queryClient.clear()` for normal business mutations. Clear tenant-bound cache only on logout/session loss/tenant boundary change.

## 18. Polling policy

Async resources may include import batches, campaign runs/messages and generated documents.
Polling begins only when the response status is explicitly non-terminal. Current import
create performs processing synchronously despite returning `202`, so a terminal response
must not start polling.

```text
non-terminal -> poll with bounded interval
terminal     -> stop polling
window hidden/background -> slow down or pause
mutation completed -> immediate refetch
component unmounted -> no polling
```

No permanent `setInterval` loops. Polling must be driven by query state and terminal-status predicates.

## 19. Error handling contract

- `400` -> form/filter validation; use `ProblemDetail.code` and `errors` where available;
- `401` -> one refresh attempt, then login;
- `403` -> access denied, no refresh loop;
- `404` -> not found / foreign-tenant-safe response;
- `409` -> reload authoritative resource and map stable business `code` to UX;
- `5xx` -> generic operation failure with `traceId/correlationId` available for support, never raw exception text.

Known conflict codes used by frontend workflows include:

```text
VERSION_CONFLICT
IDEMPOTENCY_CONFLICT
ALLOCATION_EXCEEDS_PAYMENT
ALLOCATION_EXCEEDS_INVOICE
CURRENCY_MISMATCH
CUSTOMER_MISMATCH
COLLECTION_CASE_ALREADY_ACTIVE
```

Frontend must tolerate unknown future `code`/enum values with a safe fallback label instead of crashing.

## 20. Feature module ownership

```text
features/auth
features/dashboard
features/customers
features/contracts
features/receivables
features/collections
features/templates
features/campaigns
features/messages
features/imports
features/files
features/admin
features/profile
```

Route composition and business modules follow the implemented dependency direction:

```text
app -> pages -> features -> entities -> shared
```

Entities own transport DTO/API/query keys for a domain; features own user intents/forms;
pages compose routes; shared owns transport/session/UI primitives only. Reverse and
cross-feature imports are architecture-test failures.

## 21. Frontend scaffold gate

`frontendweb/` and FW0–FW2 already exist. Before FW3, recover FW2C/FW2D into `main`, close
FW2E and approve the money transport decision. Historical test counts are not readiness
evidence; use current required CI checks for the exact commit.
