# FW6 — Collection work queue

Status: REVIEWED / OPERATIONAL FILTER AND HISTORY PAGING GATE

Depends on: FW4 customers, FW5 receivables, approved decimal transport

Suggested branches: `feat/frontendweb-fw6a-collection-queue`,
`feat/frontendweb-fw6b-collection-case-workflows`

## Цель

Реализовать рабочую очередь взыскания и case workspace для lifecycle, promises,
disputes, actions и timeline, сохраняя разделение collection workflow и receivable finance.

## Backend contract

`CollectionController`, `ROLE_HUMAN`:

- paged `GET /api/v1/collection-cases` and case detail;
- create/update/start/hold/close commands;
- promise create/list/fulfill/break/cancel;
- dispute create/list/resolve/cancel;
- action create/list/complete/cancel;
- immutable timeline read.

List item already contains customer/invoice/assignee labels, financial context and earliest
pending next action. No row fan-out is permitted.

### Required backend closure

- add fixed queue filters `nextActionOverdue`, `nextActionDueFrom/To` and allow-listed
  `nextActionDueAt` sort; current API supports only customer/invoice/status/priority/assignee;
- provide a bounded permission-aware assignee lookup instead of downloading the unpaged
  tenant user list;
- promises, disputes, actions and timeline currently return unbounded lists: page them or
  enforce documented hard per-case limits before production acceptance;
- apply the approved decimal transport to promise amounts and projected outstanding money.

## Routes

```text
/collections
/collections/:caseId/{overview|promises|disputes|actions|timeline}
```

Queue URL owns page/size/sort/status/priority/assignee/customer/invoice and next-action due
filters that exist in the hardened backend contract.

## Queue contract

- work queue uses server projection and stable ordering;
- next action due/overdue comes from backend;
- links to customer/invoice retain `returnTo` only when it is a validated internal path;
- saved personal views are out of scope unless backend persistence is added.

## Case workflow contract

- lifecycle buttons derive from explicit backend status plus permissions;
- every versioned command sends the current `version`;
- successful command replaces/invalidates authoritative detail and queue row;
- promise/dispute/action panels have independent loading/error states;
- timeline is read-only and sorted according to backend contract;
- child panels use their own page/cursor state; opening a long-lived case does not load its
  entire history;
- collection status never changes invoice payment status in frontend state.

## Conflict semantics

On `409`:

1. preserve unsent form input;
2. fetch current case detail and version;
3. show what changed at a safe summary level;
4. require explicit re-apply or cancel;
5. never automatically repeat close/resolve/complete commands.

## Tests

- queue projection test proves no customer/invoice/assignee row requests;
- URL filter round-trip and overdue navigation;
- child history paging/limit boundary tests;
- lifecycle action availability matrix;
- version and 409 reconciliation tests;
- promise/dispute/action terminal-state tests;
- invalidation of queue/detail/dashboard after commands;
- timeline rendering and PII/error safety.

## Implementation order

1. Backend operational filters, assignee lookup and child-history bounds.
2. Case DTO/API/query keys/filter codec.
3. FW6A work queue and links.
4. Detail shell and lifecycle commands.
5. Promises, disputes, actions and timeline.
6. Concurrency and cross-domain invalidation scenarios.

## Не входит

Client-side assignment engine, financial recalculation, offline case editing, arbitrary
workflow builder and bulk lifecycle commands.

## Definition of Done

- queue is usable without N+1 requests;
- stale-version commands cannot overwrite newer state silently;
- workflow and finance states remain visibly distinct;
- all lifecycle, error and accessibility scenarios pass frontend CI.
