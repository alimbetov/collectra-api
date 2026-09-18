# FW4B — Customer read-only detail implementation contract

Status: IMPLEMENTED

Depends on: FW2 foundation, FW3 query conventions, FW4A merged in `main` as PR #91.

Suggested implementation branch: `feat/frontendweb-fw4b-customer-detail`

## 1. Goal

Turn a customer-list row into a reload-safe customer landing page with read-only overview and
contacts. FW4B exposes authoritative server data, keeps foreign-tenant resources indistinguishable
from missing resources and creates the route/query foundation required by later customer workflows.

FW4B deliberately contains no create, edit, status, contact or segment-membership command. Those
mutations belong to FW4C and must carry the optimistic version captured from the read model.

## 2. Scope split

| Slice | Responsibility |
|---|---|
| FW4A | paged customer registry and URL-owned filters |
| FW4B | read-only customer overview and contact lists |
| FW4C | profile/status/contact mutations with `version` and `409` reconciliation |
| FW4D | segment registry and customer segment membership |
| FW4E | contracts registry, detail and customer-scoped contract tab |

Future contracts, invoices, payments, collections and activity tabs MUST NOT be rendered as dead
controls. They are added only when their corresponding slices provide a working route.

## 3. Verified backend contract and required closure

Existing tenant-scoped reads:

```http
GET /api/v1/customers/{customerId}
GET /api/v1/customers/{customerId}/emails
GET /api/v1/customers/{customerId}/phones
```

All three endpoints require authenticated `ROLE_HUMAN`. They derive tenant identity exclusively
from `TenantContext`; neither path nor query accepts `tenantId`. A customer from another tenant and
an unknown customer both return `404 NOT_FOUND`.

### 3.1 Additive detail projection closure

The current `CustomerResponse` exposes `managerUserId` and `segmentIds`, but not their labels.
FW4B MUST extend the response additively with:

```ts
managerDisplayName: string | null;
segments: SegmentSummaryDto[];
```

Resolution is performed server-side through tenant-scoped batch-capable repositories already used
by `CustomerQueryService`. The frontend MUST NOT call the user directory or one segment endpoint per
ID. The assigned manager label is part of the customer projection and does not grant directory
enumeration, so reading it does not require `USER_READ`.

The projection rules are deterministic:

- manager lookup is restricted by authenticated tenant and manager ID;
- missing/historical manager resolves to `null` while preserving `managerUserId`;
- segment lookup is restricted by authenticated tenant and membership;
- segments are ordered by case-insensitive name, then ID;
- `segmentIds` remains for compatibility and is ordered by UUID;
- all create/update/status responses that use `CustomerResponse` keep the same additive shape.

Backend integration coverage MUST prove labels, deterministic ordering and foreign-tenant safety.
OpenAPI compatibility must accept the additive fields without weakening required `version` fields.

### 3.2 Customer detail DTO

```ts
export interface CustomerDetailDto {
  id: UUID;
  externalId: string;
  customerType: 'INDIVIDUAL' | 'COMPANY';
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  middleName: string | null;
  companyName: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'BLOCKED' | 'ARCHIVED';
  managerUserId: UUID | null;
  managerDisplayName: string | null;
  preferredLocale: string | null;
  timezone: string | null;
  customFields: unknown | null;
  segmentIds: UUID[];
  segments: SegmentSummaryDto[];
  createdAt: Instant;
  updatedAt: Instant;
  version: number;
}
```

`version` is read and retained in the query cache even though FW4B does not mutate. FW4C uses the
fresh detail response as the only source for an expected version; it must never infer or increment
the version in the browser.

### 3.3 Contact DTOs

```ts
export interface CustomerEmailDto {
  id: UUID;
  email: string;
  type: string;
  primary: boolean;
  verified: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  version: number;
}

export interface CustomerPhoneDto {
  id: UUID;
  phone: string;
  normalizedPhone: string;
  type: string;
  primary: boolean;
  verified: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  version: number;
}
```

The current nested endpoints return arrays and the application currently constrains active emails
to five and active phones to two. FW4B consumes the endpoints as-is and sorts presentation locally:
primary first, then active, then value and ID. It does not reconstruct primary or verification
state. Pagination/history retention hardening is tracked for scale-up and is not silently emulated.

## 4. Routes and navigation

Canonical routes:

```text
/customers/:customerId           -> overview
/customers/:customerId/contacts  -> contacts
```

`/customers/:customerId/overview` redirects with `replace` to `/customers/:customerId`, avoiding
two canonical URLs for one view. Query strings from the list are not copied into the detail URL.
The back-to-list link targets `/customers`; browser Back still restores the exact FW4A URL because
the list state lives in history.

The FW4A display name becomes a semantic link to the customer overview. No row-wide click handler
is used because it weakens keyboard and screen-reader behaviour.

Tabs use a labelled `<nav>` and `NavLink`/`aria-current`. Only Overview and Contacts are visible in
FW4B. Reloading or directly opening either URL reconstructs the same view.

## 5. Frontend modules

Extend the existing vertical slice:

```text
frontendweb/src/entities/customer/
  model/customer.types.ts                 # detail/contact DTOs
  api/customer.api.ts                     # getCustomer/getCustomerEmails/getCustomerPhones
  api/customer.api.test.ts
  api/customer.queries.ts                 # detail/email/phone keys

frontendweb/src/features/customer-detail/
  model/customer-detail.ts                # UUID guard and deterministic contact ordering
  model/customer-detail.test.ts
  ui/CustomerDetailHeader.tsx
  ui/CustomerDetailTabs.tsx
  ui/CustomerOverview.tsx
  ui/CustomerContacts.tsx

frontendweb/src/pages/customers/
  CustomerDetailPage.tsx
  CustomerDetailPage.test.tsx
```

Dependency direction remains `app -> pages -> features -> entities -> shared`. A feature may not
import the customer page or list feature. Shared UI/error/i18n/formatters are reused.

## 6. Query and loading contract

```ts
customerKeys.detail(customerId) // ['customers', 'detail', customerId]
customerKeys.emails(customerId) // ['customers', 'detail', customerId, 'emails']
customerKeys.phones(customerId) // ['customers', 'detail', customerId, 'phones']
```

Overview requests only customer detail. Contacts requests detail, emails and phones; the two contact
requests execute in parallel after a syntactically valid `customerId` is known. Query functions do
not accept tenant ID. Automatic business retries are disabled by the shared QueryClient policy;
the user can retry a failed resource explicitly.

Loading behaviour:

- invalid UUID: render local not-found state and send no API request;
- first detail load: accessible page spinner, no false empty content;
- contacts load: header remains visible and independent email/phone sections resolve separately;
- one contact request failure does not hide the successful section;
- refetch keeps already loaded data visible where React Query can safely do so.

## 7. Overview presentation

Header:

- back-to-customers link;
- display name and external ID;
- localized type and status badge;
- no non-functional edit/status buttons.

Overview sections:

- identity: individual name parts or company name, without inventing missing values;
- ownership/preferences: manager label, locale and timezone;
- segmentation: server-provided segment chips;
- metadata: created and updated instants via shared `Intl` formatter;
- custom fields: read-only JSON rendered as escaped text, never `dangerouslySetInnerHTML`.

Missing optional values use a localized em dash. Raw enum values are not displayed. Unknown custom
field keys are treated as customer data, not localization keys or HTML.

## 8. Contacts presentation

Email and phone sections are separate semantic lists/tables. Each row shows:

- original value (`email` or `phone`; normalized phone is not preferred display text);
- localized contact type, with a safe localized fallback for unknown future values;
- localized ACTIVE/INACTIVE status;
- primary marker;
- verified/unverified marker.

An empty email list and empty phone list have different messages. FW4B shows no add/edit/deactivate
controls. Contact versions are retained in DTOs for FW4C but are not exposed as user-facing data.

## 9. Error and security semantics

- `401`: shared refresh/session-loss flow owns navigation;
- page-level `403`: replace navigation to `/forbidden`;
- detail `404`: customer-specific not-found state with a safe list link;
- contact `404`: treat the whole customer as not found, because nested resources validate owner;
- `5xx`: shared `ProblemDetailPanel`, masked server detail and user-triggered retry;
- correlation/trace identifiers use the shared safe support-ID UX;
- raw PII is never written to console, telemetry labels, URL or error copy;
- foreign tenant IDs never produce a distinguishable detail, label or contact response.

## 10. Localization and accessibility

- add every FW4B string to both `ru` and `kk` catalogs in the same commit;
- heading hierarchy is one page `h1`, then section `h2`;
- tab navigation, back link, status, contact markers and retry actions have accessible text;
- definition lists use semantic `dt`/`dd`; contact data uses semantic tables or lists;
- narrow layouts stack cards and keep contact values breakable;
- loading and error states do not cause an empty page for assistive technology.

## 11. Tests

### Backend integration/OpenAPI

- customer detail returns manager label and sorted segment summaries;
- manager/segment resolution is tenant-scoped;
- unknown and foreign customer return the same `404 NOT_FOUND` contract;
- additive detail fields appear in generated OpenAPI and existing compatibility checks stay green.

### Unit/API

- valid/invalid UUID guard;
- deterministic email/phone presentation order without state reconstruction;
- exact encoded detail/contact paths and no `tenantId`;
- enriched detail DTO and contact arrays map without client-side lookups.

### Page/component

- FW4A customer link targets the canonical detail URL;
- direct overview and contacts URLs load correctly;
- overview does not request contacts;
- contacts issue exactly three tenant-scoped reads: detail, emails and phones;
- loading, partial contact failure, empty contacts, retry, `403` and `404` states;
- localized enum/marker labels, custom JSON escaping and timezone formatting;
- invalid UUID sends zero requests;
- tab and heading semantics are accessible.

## 12. Implementation order

1. Additive backend detail projection and integration/OpenAPI tests.
2. Detail/contact DTOs, API functions and query keys.
3. UUID/order helpers and unit tests.
4. Header, tabs, overview and contact read components.
5. Page orchestration, router paths and FW4A detail link.
6. ru/kk, responsive styles and page/API tests.
7. `npm run typecheck`, `npm run test:ci`, `npm run build`, repository CI and narrow merge.

## 13. Out of scope

- customer create/edit/status commands;
- add, replace, activate, deactivate or set-primary contact commands;
- segment membership mutations or segment registry;
- contracts, invoices, payments, collections or activity tabs;
- client-side user/segment joins;
- generic JSON editor or arbitrary HTML rendering;
- exposing tenant ID, internal persistence fields or raw version as UI content.

## 14. Definition of Done

- customer rows open a canonical, reload-safe detail route;
- overview and contacts render authoritative tenant-scoped backend data;
- manager and segment labels require no directory download or N+1 HTTP calls;
- overview sends one request; contacts sends exactly detail plus email and phone requests;
- read-only UI contains no misleading mutation affordance;
- `version` is preserved for FW4C and no browser-generated version exists;
- foreign/missing resources, PII, ProblemDetail and support IDs follow shared security rules;
- backend integration/OpenAPI and frontend unit/API/page/architecture checks are green;
- implementation is independently mergeable before FW4C.
