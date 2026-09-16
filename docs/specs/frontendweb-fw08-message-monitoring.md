# FW8 — Message delivery monitoring

Status: DRAFT / API READY

Depends on: FW7 campaign run route

Suggested branch: `feat/frontendweb-fw8-message-monitoring`

## Цель

Создать read-only operational view сообщений конкретного campaign run с фильтрами,
деталями попыток/ошибок/вложений и строгим PII/provider boundary.

## Backend contract

`MessageController`, `CAMPAIGN_READ`:

- `GET /api/v1/campaigns/{campaignId}/runs/{runId}/messages`;
- `GET .../messages/{messageId}`.

List is `SliceDto`: it exposes `hasNext`, not `totalElements/totalPages`. Filters are
status/channel/customer/page/size. Detail exposes masked destination, normalized safe
error summary, provider message ID and attachment readiness; never raw provider payload.

## Routes and modules

```text
/campaigns/:campaignId/runs/:runId/messages
/campaigns/:campaignId/runs/:runId/messages/:messageId
src/entities/message/*
src/features/messages/filters/*
src/pages/messages/*
```

## List contract

- next/previous pagination respects slice semantics and does not invent last page;
- filter state is URL-owned and changing filters resets page;
- status/channel badges use centralized domain maps with unknown-value fallback;
- destination is rendered exactly as masked by backend;
- customer link uses ID but does not fetch a label per row unless list projection is later
  expanded by backend;
- refresh/polling follows the active run terminal-state policy from FW7.

## Detail contract

- displays status, attempts, processing/retry/sent timestamps, safe error and attachments;
- providerMessageId is diagnostic text, not a provider URL;
- error code is mapped to localized text; safe backend summary may be shown, raw exception
  and provider body may not;
- attachment filename/content type/size/readiness only; download uses File API when an
  authorized file reference is provided by the contract;
- copy actions have explicit feedback and validated bounded values.

## Security and operations invariants

- tenant/campaign/run/message hierarchy is enforced by backend and mirrored in query keys;
- no raw destination, rendered body, credentials or provider response in UI/logs;
- manual retry/cancel is excluded until backend defines eligibility, idempotency, audit,
  rate limiting and counter impact;
- React does not infer terminal result from attempt count.

## Tests

- Slice pagination and filter URL tests;
- hierarchy/query-key isolation tests;
- masked PII and safe-error rendering fixtures;
- unknown status/channel forward-compatibility;
- attachments readiness display;
- polling stop/pause tests;
- assertion that no manual mutation controls exist.

## Implementation order

1. Message DTO/API/query keys and domain display maps.
2. Slice-aware list/filtering.
3. Detail drawer/page and attachment states.
4. Run polling integration.
5. security/accessibility/MSW scenarios.

## Не входит

Manual retry, provider dashboards, message body preview, raw destination, provider request
or response payload and credential management.

## Definition of Done

- monitoring is fully read-only and slice-aware;
- PII/provider boundaries are verified by tests;
- direct detail URL and refresh work under tenant hierarchy;
- frontend CI is green.
