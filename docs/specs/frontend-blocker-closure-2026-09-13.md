# FrontendWeb — Blocker Closure Record

Status: IMPLEMENTED IN BRANCH / VERIFICATION PENDING  
Date: 2026-09-13  
Branch: `spec/post-slice10a-hardening-roadmap`

This record supersedes the open-blocker conclusions in `frontend-ui-process-api-review-2026-09-13.md`. The earlier review remains useful as the finding history; this document records how each finding was resolved.

## Closure matrix

| ID | Finding | Resolution | State |
|---|---|---|---|
| G-UI-01 | Web login required tenant UUID | added `POST /api/v1/auth/login/by-slug`; legacy UUID login preserved | IMPLEMENTED |
| G-UI-02 | Customer list would require frontend row fan-out | enriched `CustomerListItem` with primary contacts, manager label and segment summaries using bounded batch loads | IMPLEMENTED |
| G-UI-03 | Collection queue lacked display/next-action projection | enriched `CaseItem` with customer/invoice/assignee labels and earliest pending action using bounded batch loads | IMPLEMENTED |
| G-UI-04 | Dashboard DTO unknown | exact existing Dashboard read models audited and frozen for frontend contract | CLOSED BY AUDIT |
| G-UI-05 | Tenant administration not DTO-ready | member identity projection enriched; current member roles read endpoint added; existing invitation/role/permission/session APIs audited | IMPLEMENTED |
| G-UI-06 | SourceSchema/MappingProfile contracts unknown | current definition/version/field/rule/test/validation/lifecycle endpoints audited | CLOSED BY AUDIT |
| G-UI-07 | Template Builder contract unknown | existing builder catalogue, draft/document validation/preview, builder versions and assets audited | CLOSED BY AUDIT |
| G-UI-08 | CampaignSelection unknown | exact six-field selection contract audited | CLOSED BY AUDIT |
| G-UI-09 | Message read DTO unknown | MessageSlice/MessageListItem/MessageDetail/AttachmentDetail audited | CLOSED BY AUDIT |
| G-UI-10 | shared Page/Slice/ProblemDetail contract unknown | current page/slice variants and ProblemDetail extensions documented with frontend normalization rule | CLOSED BY AUDIT |

## Architectural decisions

### Login

Normal `frontendweb` login is:

```text
tenantSlug + email + password
    -> POST /api/v1/auth/login/by-slug
```

Unknown tenant slug and invalid user credentials both produce the same external authentication failure. The backend resolves slug to tenant internally.

### Screen-oriented read projections

Operational lists may contain bounded presentation data required to render a table. This is a read-model concern, not duplicated domain ownership.

Forbidden frontend pattern:

```text
GET page of 50 rows
  -> 50 customer requests
  -> 50 user requests
  -> 50 segment/action requests
```

Preferred pattern:

```text
GET one paged endpoint
  -> backend page query
  -> bounded batch enrichment queries
  -> screen-ready list projection
```

### Bounded-context dependency

Customer and Collection read models do not depend directly on identity infrastructure. Tenant-user display data is exposed through `IdentityDirectoryService` at the application boundary.

### Provider independence

No blocker closure introduces provider-specific UI semantics. EMAIL/SMS/WHATSAPP/TELEGRAM/IN_APP continue to use provider-neutral message contracts and deterministic mock adapters until real provider phases.

## Regression evidence added

`FrontendBlockerClosureIntegrationTest` covers:

1. login by tenant slug and non-enumerating invalid slug response;
2. customer list projection with primary email/phone and segment summary;
3. collection work-queue projection with customer/invoice/financial/next-action data;
4. tenant administration member identity and role-read contract.

## Documentation reconciliation

The following documents are now aligned with the closed blocker state:

- `frontend-ui-information-architecture.md` — `REVIEWED / API-ALIGNED PRODUCT/UI BASELINE`;
- `frontend-user-processes.md` — `REVIEWED / API-ALIGNED BUSINESS-SYSTEM BASELINE`;
- `frontend-screen-api-matrix.md` — `FRONTEND CONTRACT READY / DTO FREEZE NEXT`.

## Verification gate

Implementation is not considered build-verified until the branch passes the normal project test suite. Required verification:

```bash
./mvnw spotless:check
./mvnw test
./mvnw clean verify
```

The broader frontend start gate still additionally requires the project-level Slice 10A/test-infrastructure/CI conditions documented elsewhere. Closing G-UI-01..10 removes the frontend API-design blockers; it does not waive those reliability gates.

## Next artifact after verification

Create `frontend-react-api-contract.md` containing exact TypeScript contracts, transport adapters, API functions, TanStack Query keys, mutation invalidation rules, auth refresh behavior, permission guards and screen-to-query mapping.
