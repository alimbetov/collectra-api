# FW12 — Tenant administration and profile

Status: REVIEWED / PAGING, DTO AND REVISION HARDENING REQUIRED

Depends on: FW2 RBAC/i18n/error foundation

Suggested branches: `feat/identity-directory-paging`,
`feat/frontendweb-fw12a-profile-security`, `feat/frontendweb-fw12b-tenant-admin`

## Цель

Реализовать profile/security и tenant administration для members, invitations, roles,
permissions and sessions, не смешивая tenant и platform administration.

## Backend contract

- current profile/update/password/sessions/revoke;
- tenant users and membership roles/status/sessions;
- invitations create/list/revoke and public accept flow;
- roles CRUD and permissions catalogue;
- granular `USER_*`, `ROLE_*` permissions.

Platform super-admin `/api/v1/platform/**` остаётся отдельным route tree/app surface и не
входит в tenant MVP.

## Required backend hardening

Current member and invitation endpoints return unbounded `List`. Before large-tenant
production acceptance introduce fixed-filter paged projections:

```text
GET /api/v1/identity/users?search&status&page&size&sort
GET /api/v1/identity/invitations?email&status&page&size&sort
```

Tenant predicate, stable ordering, bounded size, safe email projection and PostgreSQL
query-count/security tests are mandatory. Permissions catalogue/role list may remain
bounded configuration lists with documented upper limits.

Normalize the member response field. Current backend JSON returns `id`, while
`frontend-react-api-contract.md` calls it `membershipId`. Preferred public DTO uses explicit
`membershipId`; if migration is staged, a boundary adapter maps `id` once and no feature
uses both names.

Role responses expose no JPA revision and update accepts no expected version. Add
`revision`/ETag precondition for role update/delete (and preferably membership role
replacement) before claiming conflict-safe concurrent administration. Bound tenant role
count or page the list.

Add tenant-scoped direct member detail and role detail endpoints for the routed
`/:membershipId` and `/:roleId` pages. Reload must not scan an arbitrary member/role list.

## Routes

```text
/profile
/profile/security
/profile/sessions
/administration/members
/administration/members/:membershipId
/administration/invitations
/administration/roles
/administration/roles/:roleId
```

## FW12A — profile and security

- edit display/locale/timezone through current-user API;
- extend auth/session model with one controlled `replaceUser`/`refreshMe` path; the current
  AuthContext has no profile refresh method;
- locale/timezone change updates AuthContext and I18n/Intl context from the authoritative
  response without requiring logout/login;
- password change clears sensitive form state;
- current sessions identify current/active/revoked state safely;
- revoke one/all sessions requires confirmation and handles current-session logout.

## FW12B — tenant administration

- members use paged directory projection; role IDs load only when a member is opened;
- no role request per member row;
- assign roles requires `ROLE_ASSIGN`, status change `USER_BLOCK`, session operations
  `USER_READ/USER_UPDATE`;
- invite uses selected role IDs, handles duplicate/pending conflicts and supports revoke;
- role editor groups permission catalogue by module;
- system roles cannot expose unsupported destructive actions;
- deleting/updating an in-use or protected role maps backend conflict to an actionable UI;
- UI prevents accidental self-lockout where current identity is known, while backend remains
  authoritative.

## RBAC invariants

- route visibility uses exact backend permission codes;
- administration root is visible when at least one child capability is available; each
  child route checks its exact permission (`USER_READ`, `USER_INVITE`, `ROLE_READ`, etc.);
- action permission checks are centralized and testable;
- user cannot grant permissions merely because UI has stale role catalogue;
- role/membership mutations invalidate current `me` when they may affect the current user;
- a resulting loss of access is handled as expected state, not an infinite 403 retry.

## Tests

- profile locale/timezone live update;
- password form secret clearing;
- session revoke/current-session logout;
- paged member/invitation filters and no role N+1;
- direct member/role URL reload uses one detail request, not list scanning;
- `id` -> `membershipId` boundary normalization contract;
- exact permission/action matrix;
- role protection, duplicate invite, self-lockout and 409 cases;
- concurrent role revision conflict and bounded role-catalogue test;
- current-user permission cache refresh after role mutation;
- tenant/platform route isolation architecture test.

## Implementation order

1. Backend member/invitation paging, membership DTO naming and role revision hardening.
2. Profile/security/session UI.
3. Member directory/detail/roles/status/sessions.
4. Invitations.
5. Roles/permissions and current-user rebootstrap.

## Не входит

Platform administrator UI, SSO/provider configuration, impersonation, audit-log analytics
and custom permission creation outside the backend catalogue.

## Definition of Done

- tenant admin lists are bounded and do not fan out role requests;
- exact permission loss/gain is reflected without relogin where contract permits;
- self/current-session destructive actions have deterministic UX;
- tenant and platform surfaces remain isolated and CI is green.
