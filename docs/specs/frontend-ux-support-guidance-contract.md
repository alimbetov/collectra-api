# FrontendWeb — UX Guidance and Support Contract

Status: IMPLEMENTATION-READY SYSTEM ANALYSIS BASELINE

## 1. Purpose

Collectra must explain operational work inside the product. A user should not need backend terminology, a runbook, or developer assistance to understand a normal business state, a blocked action, or the next safe step.

This contract complements:
- `frontend-ui-information-architecture.md`;
- `frontend-user-processes.md`;
- `frontend-screen-api-matrix.md`;
- FW3–FW12 feature specifications.

The backend remains authoritative. Guidance explains state; it never invents business decisions in React.

## 2. UX support model

Every operational screen answers five questions in this order:

1. **Where am I?** — business object/process and current status.
2. **What does this mean?** — short business-language state explanation.
3. **What can I do now?** — only valid/authorized primary and secondary actions.
4. **What happens next?** — consequence, asynchronous wait, or resulting state.
5. **What do I do if it fails?** — recovery action plus safe Support ID when escalation is needed.

Progressive disclosure:

```text
always visible        business label + status + primary next action
contextual            short hint / why action is disabled / consequence
on problem            actionable recovery guidance
support details       Support ID + safe diagnostics, never secrets/raw provider payload
```

Do not expose implementation mechanics such as UUID, optimistic-lock revision, Idempotency-Key, bounded polling/backoff, storage key, raw provider response, tenantId, or Java/backend terminology unless the user is explicitly in a technical administration/diagnostics surface.

## 3. Shared UI primitives required

### 3.1 PageGuidance

Placed below title/status on complex process pages.

Fields:
- title — one sentence describing the current business objective;
- description — max two short sentences;
- nextAction — optional context-sensitive instruction;
- learnMore — optional expandable detail.

Example:

```text
Collection case · In progress
Work the overdue receivable by recording the next customer commitment,
dispute, or follow-up action.

Next: record the agreed promise date or schedule the next contact.
```

### 3.2 ContextHint

Inline help next to a field, KPI, status or non-obvious setting.

Use for:
- business definition;
- input expectation;
- consequence that is not obvious;
- difference between similar concepts.

Do not use tooltip-only help for information required to complete a task. Required guidance must be visible and keyboard/mobile accessible.

### 3.3 ActionGuard / DisabledReason

A disabled action must explain why and how to unblock it.

Bad:
`[Publish disabled]`

Good:
```text
Publish
Unavailable until validation succeeds.
Action: Validate version.
```

Permission denial is different:
```text
You can view this campaign, but your access does not allow activation.
Ask a tenant administrator for Campaign Manage permission.
```

Never imply that frontend permission checks are authoritative security.

### 3.4 OperationOutcome

After a significant command show:
- what completed;
- which object changed;
- what happens next;
- direct next navigation when useful.

Avoid generic `Saved` when a business consequence can be stated.

### 3.5 ProblemRecoveryPanel

Extend current `ProblemDetailPanel` from generic detail/retry into a code-driven recovery model:

```ts
type RecoveryAction =
  | 'RELOAD'
  | 'RETRY_SAME_INTENT'
  | 'EDIT_INPUT'
  | 'OPEN_EXISTING'
  | 'CHOOSE_COMPATIBLE_RESOURCE'
  | 'CONTACT_ADMIN'
  | 'CONTACT_SUPPORT'
  | 'NONE'
```

View model:
- userTitle;
- explanation;
- recoveryAction;
- recoveryLabel;
- supportId;
- retryAllowed.

The frontend maps stable `ProblemDetail.code`; unknown codes fall back safely.

Required mappings include:
- VERSION_CONFLICT -> explain newer server state; reload/review;
- IDEMPOTENCY_CONFLICT -> do not blindly retry; review changed intent;
- ALLOCATION_EXCEEDS_PAYMENT -> reload available payment balance;
- ALLOCATION_EXCEEDS_INVOICE -> reload invoice outstanding balance;
- CURRENCY_MISMATCH -> select same-currency resource;
- CUSTOMER_MISMATCH -> select resource for same customer;
- COLLECTION_CASE_ALREADY_ACTIVE -> open existing case when discoverable;
- 401 -> session recovery/login;
- 403 -> explain access boundary, no retry;
- 404 -> not-found/foreign-tenant-safe message;
- 5xx -> safe generic explanation + Support ID + retry only when operation semantics permit.

### 3.6 ProcessProgress

Use only for real multi-step workflows:
- campaign creation;
- import configuration/execution;
- template authoring/publish;
- integration setup.

Each step states:
- completed/current/blocked;
- completion criterion;
- next safe action.

Do not fabricate progress percentages when backend has no percentage.

### 3.7 BusinessStatusHelp

Central dictionary per domain:
```ts
status -> {
  label,
  tone,
  meaning,
  nextActions,
  terminal
}
```

Unknown enum: `Unknown (<raw>)` plus non-blocking support-safe fallback.

## 4. Content rules

User-facing copy follows:

```text
business object → business state → consequence → next action
```

Prefer:
- "Payment is not fully allocated. 25,000 KZT remains available."
- "This promise is overdue. Confirm whether the customer paid or mark the promise as broken."

Avoid:
- "version=7";
- "polling disabled";
- "bounded backoff";
- "Idempotency-Key reused";
- "HTTP 409";
- raw IDs as primary labels.

IDs may appear in an expandable "Technical details" block when useful for support.

## 5. Screen guidance matrix

| Area | User question the screen must answer | Always-visible guidance | Contextual support / next action |
|---|---|---|---|
| Dashboard | What needs attention today? | KPI meaning + as-of time | KPI opens the exact filtered work queue; explain stale/as-of data |
| Customers | Who is this customer and what work exists? | status, primary contact, relationship summary | missing primary contact -> add contact; inactive -> explain effects |
| Contracts | Is the commercial relationship usable? | lifecycle status + validity | explain Suspend/Close/Cancel consequence before command |
| Receivables | What is owed and why is it actionable? | original/paid/outstanding, due state, authoritative overdue | overdue -> open collection case; paid -> no collection action |
| Payments | What money arrived and where was it applied? | amount, unallocated balance, allocation state | allocation mismatch/excess -> exact corrective action |
| Collections | What should the officer do next? | case state, debt context, next action/due state | promise/dispute/action cards explain state transitions and next step |
| Templates | Is content safe/ready to use? | Draft/Validated/Published meaning | validation errors point to field/block; Publish explains immutability/use |
| Campaigns | Is the campaign ready and what will happen? | audience/template/channel/schedule/readiness | Validate -> fix blockers; Activate -> makes executable; Run -> creates delivery run |
| Messages | What happened to this communication? | normalized delivery state | retry waiting -> when; failed -> safe reason; attachment blocker -> what is missing |
| Imports | Did my data load and what must I fix? | processing/result counts | failed rows -> diagnostics; terminal state stops user-facing "processing" language |
| Files | What is this file used by? | business purpose + owner/context | deletion consequence; safe download state |
| Administration | Why can/can't this user act? | membership status + assigned roles | permission descriptions in business language; last-admin conflicts actionable |
| Integrations | Is data exchange configured and healthy? | source/auth/mapping readiness | setup checklist, last safe test result, support diagnostics |

## 6. Process-specific guidance

### Customer onboarding
Show a lightweight completion checklist, not a forced wizard:
- profile created;
- primary email/phone (optional/required according to domain);
- segment membership;
- contract;
- receivable.

Do not mark optional business objects as errors.

### Receivable detail
Replace technical-only facts with a decision block:
```text
Payment status: Partially paid
Outstanding: 75,000 KZT
Due: 8 days overdue

Recommended next operation:
Open or review the collection case.
```
"Recommended" here means deterministic workflow routing from backend state, not business scoring.

### Payment allocation
Before Allocate:
```text
Available to allocate: X
Invoice outstanding: Y
Currency: KZT
Customer: Acme LLP
```
Explain that allocation links received money to an invoice and changes authoritative invoice balances.

Before Reverse:
state amount, affected invoice and reason requirement. Confirmation must say balances will be recalculated by the server.

### Collection workspace
This requires the richest embedded support.

Header:
```text
Outstanding: ...
Case: IN_PROGRESS
Next action: CALL · due today
Promise: ACTIVE · due 30 Sep
Dispute: none
```

Sections include one-line definitions:
- Promise — customer's commitment to pay by a date;
- Dispute — customer contests debt/amount/condition;
- Action — internal follow-up task;
- Timeline — authoritative chronological history.

Buttons use business labels ("Start work", "Put on hold", "Close case"), not raw commands.

Close dialog requires close-reason selection and explains PAID/SETTLED versus operational cancellation.

### Template authoring
Expose a visible readiness sequence:
```text
Draft → Validate → Preview → Publish
```
Each validation issue must include location/path and corrective text. Raw validation code is secondary technical detail.

Published means read-only/use-ready; Archive removes future selection but must not imply historical campaigns are changed unless backend contract says so.

### Campaigns
Header readiness card:
```text
Audience       Ready / issue
Template       Published / issue
Channel        Email
Schedule       ...
Attachment     Ready / generating / failed
```

"Activate" explains that configuration becomes executable.
"Create run" explains that recipients/messages are materialized from current authoritative eligibility.

Do not expose generated command UUID.

### Message delivery
Translate provider-neutral statuses into operator language:
- PREPARED/QUEUED -> waiting for processing;
- PROCESSING -> delivery attempt in progress;
- RETRY_WAIT -> temporary failure; next retry time;
- SENT -> accepted/sent according to backend contract, not necessarily opened/read;
- FAILED -> no automatic retry; safe reason + next support/business action;
- SKIPPED -> not sent because eligibility/business rule prevented delivery.

Never claim delivered/read unless backend has authoritative provider receipt semantics.

### Imports
Remove implementation-language copy such as Idempotency-Key and bounded backoff from ordinary operator UI.

Creation:
```text
Upload the source file and choose how Collectra should interpret it.
If the connection is interrupted, Collectra safely prevents accidental duplicate submission.
```

Detail:
- Processing -> "Import is being processed. Status updates automatically.";
- Completed -> counts + links to created business records when available;
- Failed/partial -> "Review diagnostics" with record/field errors and corrective guidance.

### Administration
Permission catalogue needs a human description:
```text
COLLECTION_MANAGE
Manage collection cases
Allows starting, holding and closing cases and managing promises/disputes/actions.
```
Raw permission code is secondary.

## 7. Empty-state taxonomy

Do not use one generic empty state.

1. **First-use** — explain purpose + create action.
2. **No filter results** — explain filters + clear filters.
3. **No related records** — explain relationship + contextual create/open action.
4. **Permission-limited** — explain view/access limitation without pretending data does not exist.
5. **Process-complete** — positive state, e.g. no overdue work.
6. **Unavailable/degraded** — error/retry/support, not an empty state.

## 8. Confirmation policy

Confirmation is mandatory when an operation:
- is destructive or hard to reverse;
- changes lifecycle availability;
- sends/starts external communication;
- reverses money allocation;
- closes/cancels a collection case;
- publishes/archives content;
- revokes access/session.

Dialog copy must include:
1. object;
2. consequence;
3. reversibility;
4. resulting state;
5. explicit verb.

Avoid "Are you sure?".

## 9. Support diagnostics

Every recoverable error surface may expose:
```text
Support ID: <correlationId or traceId>
[Copy Support ID]
```

For asynchronous business objects also show safe identifiers in an expandable block:
- import batch ID;
- campaign/run/message ID;
- generation job ID;
- file ID.

Never show:
- access/refresh token;
- provider credentials;
- storage credentials;
- raw authorization headers;
- unmasked destination where contract masks it;
- raw 5xx stack/detail.

Support escalation text:
```text
If the problem continues, send the Support ID and the time of the operation to support.
```

## 10. Accessibility and localization

- hints required to finish a task are not title-only tooltips;
- every hint/error is linked with `aria-describedby`;
- status is conveyed by text, not color alone;
- async changes use appropriate status/live regions without noisy repeated announcements;
- confirmation focus returns to the triggering action;
- copy is translation-key driven; no mixed Russian/English operational vocabulary in one locale;
- dates/currency use user locale/timezone while backend business date remains authoritative.

## 11. Analytics/support events

Do not record sensitive business payloads. Record safe UX events to discover confusing workflows:
- help opened by screen/topic;
- validation failure code;
- command conflict code;
- retry/reload action selected;
- empty-state action selected;
- support ID copied.

These events measure where users need help; they do not replace backend audit events.

## 12. Current implementation audit

### Strengths already present
- permission-aware navigation/actions;
- centralized `ProblemDetailPanel`;
- safe Support ID extraction from correlation/trace ID;
- `FormField.hint` with aria description;
- `ConfirmDialog`;
- `EmptyState`;
- bounded import polling;
- optimistic conflict handling in template/campaign flows;
- unknown-value strategy is specified;
- provider-neutral message model.

### Gaps
- guidance is not centralized as a product contract;
- ProblemDetail uses server 4xx detail but does not map stable codes to recovery actions;
- disabled actions generally do not explain prerequisites;
- many pages expose raw status/UUID/version/revision;
- collection workspace forms are command-centric and under-explained;
- import pages expose Idempotency-Key and polling implementation language;
- campaign/template lifecycle consequences are inconsistent;
- generic empty states do not distinguish first-use/filter/permission/process-complete;
- support escalation is Support ID only, without "what to do next";
- copy mixes English/Russian and technical/business vocabulary;
- no consistent "as of" explanation for dashboard/reporting projections.

## 13. Implementation slices

### UX-S1 — shared guidance primitives
- `PageGuidance`;
- `ContextHint`;
- `ActionGuard`;
- enhanced `ProblemRecoveryPanel`;
- `ProcessProgress`;
- `BusinessStatusHelp`;
- typed error-code recovery registry.

### UX-S2 — highest-support operational screens
- Collections;
- Receivables/Payments;
- Imports;
- Campaign/Message monitoring.

### UX-S3 — authoring/configuration
- Templates;
- Integration setup;
- Files.

### UX-S4 — identity/support
- Administration;
- Profile/security;
- global 403/404/error routes;
- support diagnostics pattern.

### UX-S5 — dashboard/reporting
- KPI definitions;
- as-of/freshness explanation;
- drill-down actions;
- VC-9 analytics guidance and raw/projection implementation details kept hidden from tenant users.

## 14. Definition of Done

A screen is support-ready when:
- the primary business objective is clear without documentation;
- current status has a business-language meaning;
- every non-obvious field has accessible contextual help;
- disabled significant actions explain prerequisites;
- significant commands explain consequence before execution;
- success explains what changed/what happens next;
- known business errors provide a deterministic recovery action;
- unknown/server errors provide safe Support ID and escalation guidance;
- no backend implementation detail leaks into ordinary operator copy;
- empty state type is intentional;
- permission denial and missing data are not conflated;
- copy is localized consistently;
- frontend never becomes authoritative for business state.
