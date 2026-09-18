# FW4C — Customer editing and optimistic concurrency

Status: READY FOR IMPLEMENTATION

Depends on: FW4A and FW4B (IMPLEMENTED), backend customer concurrency contract (PR #87)

Implementation slices:

- FW4C1: `feat/frontendweb-fw4c1-customer-profile-status`
- FW4C2: `feat/frontendweb-fw4c2-customer-contacts`

## 1. Goal

Add tenant-scoped customer profile, lifecycle and contact mutations to the existing detail page.
Every update must use the authoritative server version, surface stale writes explicitly and
refresh only affected React Query projections. The browser must never invent a tenant ID,
version, permission or successful state.

This specification is split into two independently releasable PRs so profile/status behavior
can be reviewed before contact rules and cross-projection invalidation are added.

## 2. Audited baseline on `main`

The following backend contracts already exist and MUST be consumed rather than duplicated:

| Intent | Endpoint | Concurrency |
|---|---|---|
| Edit customer profile | `PUT /api/v1/customers/{id}` | customer `version` required |
| Change customer status | `PATCH /api/v1/customers/{id}/status` | customer `version` required |
| Add email | `POST /api/v1/customers/{id}/emails` | creates a new resource; no version |
| Edit email metadata | `PATCH /api/v1/customers/{id}/emails/{emailId}` | email `version` required |
| Add phone | `POST /api/v1/customers/{id}/phones` | creates a new resource; no version |
| Edit phone metadata | `PATCH /api/v1/customers/{id}/phones/{phoneId}` | phone `version` required |

`GET /api/v1/customers/{id}` and both contact reads are already tenant-scoped. Foreign-tenant
resources are indistinguishable from missing resources. Customer commands are currently guarded
by `ROLE_HUMAN`; no customer-write permission exists, so the frontend MUST NOT invent one.

The existing domain currently accepts any transition between `ACTIVE`, `INACTIVE`, `BLOCKED`
and `ARCHIVED`. FW4C presents the four authoritative values and does not encode an unsupported
transition matrix. Introducing irreversible archive or restricted transitions is a separate
backend/domain decision.

No migration or backend API change is required by FW4C.

## 3. Shared implementation contract

### 3.1 Packages and ownership

```text
frontendweb/src/entities/customer/
  api/customer.api.ts          transport functions only
  api/customer.queries.ts      query keys and query options
  model/customer.types.ts      wire DTO and command types

frontendweb/src/features/customer-edit/
  model/customer-edit.ts       server DTO <-> form DTO and validation
  model/customer-mutations.ts  mutation factories and invalidation
  ui/CustomerEditDialog.tsx
  ui/CustomerStatusDialog.tsx

frontendweb/src/features/customer-contact-edit/
  model/customer-contact-edit.ts
  ui/CustomerContactDialog.tsx

frontendweb/src/pages/customers/CustomerDetailPage.tsx
  composition, loading/error routing; no transport DTO construction
```

Feature code may depend on `entities/customer` and `shared`; entity code MUST NOT depend on
features or pages. Do not add a form framework for this slice.

### 3.2 Version rules

- Capture `version` from the query result when a dialog opens.
- Submit exactly that captured value; never increment it client-side.
- Disable only the active intent while its request is pending.
- A successful response replaces the detail/contact cache with the returned authoritative DTO.
- `409 VERSION_CONFLICT` keeps user input visible and opens a reconciliation state with two
  explicit actions: reload current server data, or cancel and retain the draft until the dialog
  is closed. There is no automatic retry and no force-save.
- Reload resets both form values and captured version from the latest successful query.
- Other `409` codes use their specific localized message and do not masquerade as a stale write.

### 3.3 Errors and accessibility

- `400` validation `errors` map to named fields when the key is known; unknown validation keys
  and request-level errors render as a form alert.
- `403` follows the existing forbidden UX; `404` closes the mutation surface and shows the same
  safe missing-customer state as FW4B.
- `5xx` detail is masked by shared error rules; safe correlation/trace ID remains available.
- Dialog focus is trapped by the shared `Dialog`; the first invalid field receives focus after
  client or server validation.
- Pending buttons expose their existing loading state. Success is announced using `Toast`.
- Closing a dirty form, changing detail tabs, following Back, browser navigation and page unload
  require confirmation. A clean form closes immediately. Successful save marks the form clean.

## 4. FW4C1 — profile and lifecycle

### 4.1 Profile command

`CustomerUpdateRequest` is a replace-style command for all mutable profile fields:

```ts
interface CustomerUpdateCommand {
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  middleName: string | null;
  companyName: string | null;
  managerUserId: UUID | null;
  preferredLocale: string | null;
  timezone: string | null;
  customFields: unknown | null;
  version: number;
}
```

`externalId`, `customerType`, `status`, segment membership and contacts are not part of this
command. The mapper trims text and converts blank optional values to `null`. `displayName` is
required and limited to 300 characters; personal-name fields to 120; company name to 300;
locale to 35; timezone to 60. `customFields` is edited as formatted JSON, must parse before
submit, and sends `null` for an empty editor. It is rendered as text, never injected as HTML.

The editor sends the complete mutable snapshot. Omitted fields must not accidentally clear
values. The original query DTO and mutable form DTO are separate objects.

### 4.2 Manager selector

- With `USER_READ`, use the existing bounded debounced
  `GET /api/v1/identity/user-options?status=ACTIVE&page=0&size=20&search=...` query.
- Include the current manager as a stable selected option even if it is absent from the active
  result page.
- Without `USER_READ`, do not request the directory and render the current manager read-only.
  Preserve the original `managerUserId` in the update command; the user cannot assign or clear it.
- `409 INVALID_MANAGER` means the newly selected membership is no longer active. Keep the draft,
  clear no fields, refresh manager options, and ask the user to choose again.

### 4.3 Status command

The detail header exposes a status action. The confirmation dialog shows current and target
status, sends `{status, version}` and requires an explicit confirm. Selecting the current status
does not send a request. The four server values are localized in Russian and Kazakh.

Status and profile dialogs cannot submit concurrently because they share the same customer
version. If one succeeds while the other is open, the other becomes stale and must reload before
submit; closing it is also allowed.

### 4.4 Cache updates and invalidation

After successful profile or status mutation:

1. set `customerKeys.detail(id)` to the response immediately;
2. invalidate `customerKeys.all` list projections without removing the fresh detail value;
3. invalidate dashboard keys because customer/status totals may change;
4. do not invalidate email or phone queries for a profile-only command.

Implementation SHOULD expose named helpers so tests assert this matrix rather than React Query
internals spread across components.

## 5. FW4C2 — contact mutations

### 5.1 Create commands

```ts
interface EmailCreateCommand { email: string; type: string | null; primary: boolean }
interface PhoneCreateCommand { phone: string; type: string | null; primary: boolean }
```

Email uses browser email validation plus backend validation and is capped at 320 characters.
Phone is nonblank and capped at 40. Contact type is optional and capped at 20; the UI offers
`WORK`, `HOME`, `MOBILE`, `OTHER` while preserving an unknown existing type in edit mode.

The backend normalizes stored email/phone values. The UI displays the value returned by the
server and does not attempt to duplicate normalization.

### 5.2 Patch commands

Contact values are immutable in the current API. Edit mode changes only `type`, `primary` and
`status`, with the contact's own captured `version`:

```ts
interface ContactPatchCommand {
  type: string | null;
  primary: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  version: number;
}
```

Changing an address/number is deliberately not simulated as an edit. A user deactivates the old
contact and creates a new one. Verified state is read-only.

### 5.3 Contact invariants

- At most five active emails and two active phones; map `CONTACT_LIMIT_REACHED` explicitly.
- Duplicate normalized value maps `DUPLICATE_CONTACT` and keeps the create draft.
- Making a contact primary may atomically demote the previous active primary.
- An inactive contact cannot be primary. The form automatically clears `primary` when status is
  set to `INACTIVE`, and the server remains authoritative via `INVALID_STATE_TRANSITION`.
- React must replace the full corresponding contact collection after a successful mutation or
  refetch it; patching only the edited row is unsafe because another primary may be demoted.

### 5.4 Cache invalidation

After any email command:

- invalidate/refetch `customerKeys.emails(id)`;
- invalidate customer list projections because `primaryEmail` may change;
- leave phone queries untouched.

After any phone command, use the symmetric phone matrix. Customer detail itself currently has no
contact projection and does not require a network refetch; keeping its cache is intentional.
Dashboard invalidation is not required for contact-only mutations.

## 6. Query/mutation behavior

- All paths use `encodeURIComponent` and contain no `tenantId`.
- Mutation functions return existing response DTOs and use `apiRequest`.
- Do not optimistically display an uncommitted server state. The only optimistic behavior in
  this slice is concurrency protection by expected version.
- React Query retry stays disabled for mutation failures. User-triggered retry uses the same
  visible draft and captured version unless a reconciliation reload occurred.
- Multiple clicks are coalesced by disabling the active submit control.

## 7. Tests

### FW4C1 required tests

- mapper does not mutate the server DTO, normalizes blanks and preserves `customFields`;
- client validation covers lengths, required name and invalid JSON;
- API tests assert method, encoded path, exact JSON body and absence of `tenantId`;
- edit success uses returned version and refreshes detail/list/dashboard only;
- manager directory is not requested without `USER_READ`, while original manager ID is preserved;
- `INVALID_MANAGER` retains draft and refreshes bounded options;
- status confirmation sends the captured version once;
- `VERSION_CONFLICT` never retries or overwrites and reload updates form/version;
- dirty close/navigation and clean post-save behavior;
- `403`, safe `404`, masked `5xx`, field errors and ru/kk labels;
- keyboard labels, dialog role, error association and focus.

### FW4C2 required tests

- create and patch API body/path tests for email and phone;
- each patch uses the contact version, not customer version;
- inactive automatically clears primary;
- create/patch success refetches the complete affected collection and correct list projection;
- primary reassignment renders the server-authoritative result;
- `DUPLICATE_CONTACT`, `CONTACT_LIMIT_REACHED`, `INVALID_STATE_TRANSITION` and
  `VERSION_CONFLICT` retain the draft with localized actions;
- email failure does not hide phone content and vice versa;
- unknown contact type remains selectable/displayable;
- direct contacts URL and accessible dialog/table behavior remain green.

Local acceptance for each implementation PR:

```bash
cd frontendweb
npm ci
npm run typecheck
npm run test:ci
npm run build
```

Repository CI remains the authoritative Maven/PostgreSQL/OpenAPI/coverage gate even though FW4C
does not change Java code.

## 8. Implementation order

1. Add command DTOs and transport functions with MSW request-shape tests.
2. Add pure profile form mapper/validator and deterministic ProblemDetail mapping.
3. Implement profile dialog, bounded manager selector and dirty-form guard.
4. Implement status confirmation and FW4C1 invalidation helpers.
5. Complete ru/kk strings, page scenarios and merge FW4C1 after green CI.
6. Add contact create/patch mappers and transport functions.
7. Add email/phone dialogs and conflict/invariant UX.
8. Complete contact invalidation/scenario tests and merge FW4C2 after green CI.

## 9. Out of scope

- customer creation (`/customers/new`), bulk edit/delete and arbitrary custom-field schema UI;
- segment membership editing (FW4D) and contracts (FW4E);
- changing external ID, customer type, verified flags or contact values in place;
- new backend permissions or lifecycle rules;
- global cache clear, client-generated versions and force-save after conflict.

## 10. Definition of Done

- profile, status and contact commands use exact tenant-derived API contracts;
- stale writes are visible and cannot silently overwrite newer data;
- manager selector respects `USER_READ` and preserves an inaccessible historical assignment;
- contact limits, duplicate/primary/inactive rules have deterministic localized UX;
- cache refresh follows the documented intent matrix with no unrelated refetch storm;
- unsaved drafts are protected, dialogs are keyboard accessible and ru/kk complete;
- frontend typecheck, tests and production build pass on the supported Node engine;
- repository CI is green and both narrow PRs are merged in order.
