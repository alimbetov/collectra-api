# FW6 — Collection work queue

Status: DRAFT / API READY

Depends on: FW4 customers, FW5 receivables

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

## Routes

```text
/collections
/collections/:caseId/{overview|promises|disputes|actions|timeline}
```

Queue URL owns page/size/sort/status/priority/assignee/overdue-action/customer filters.

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
- lifecycle action availability matrix;
- version and 409 reconciliation tests;
- promise/dispute/action terminal-state tests;
- invalidation of queue/detail/dashboard after commands;
- timeline rendering and PII/error safety.

## Implementation order

1. Case DTO/API/query keys/filter codec.
2. FW6A work queue and links.
3. Detail shell and lifecycle commands.
4. Promises, disputes, actions and timeline.
5. Concurrency and cross-domain invalidation scenarios.

## Не входит

Client-side assignment engine, financial recalculation, offline case editing, arbitrary
workflow builder and bulk lifecycle commands.

## Definition of Done

- queue is usable without N+1 requests;
- stale-version commands cannot overwrite newer state silently;
- workflow and finance states remain visibly distinct;
- all lifecycle, error and accessibility scenarios pass frontend CI.
