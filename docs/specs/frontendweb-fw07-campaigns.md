# FW7 — Campaigns and runs

Status: DRAFT / BACKEND DETAIL GAP

Depends on: FW4 customers/segments, FW5 receivables, published templates from FW9 or
equivalent template selection flow

Suggested branches: `feat/campaign-run-detail-api`,
`feat/frontendweb-fw7a-campaigns`, `feat/frontendweb-fw7b-campaign-runs`

## Цель

Реализовать создание и lifecycle кампаний, подготовку run, просмотр получателей,
eligibility recheck и delivery counters без provider-specific UI.

## Backend baseline

`CampaignController`:

- list/detail/create/activate campaign;
- prepare run and paged run list;
- paged recipients;
- eligibility recheck;
- `CAMPAIGN_READ` and `CAMPAIGN_MANAGE` permissions.

### Required backend closure

Добавить tenant-scoped:

```text
GET /api/v1/campaigns/{campaignId}/runs/{runId}
```

Он возвращает `CampaignRunDto` с counters/timestamps/version и проверяет принадлежность
run указанной campaign. Иначе direct/deep link run page не может надёжно восстановиться
после reload, а сканирование paged run list недопустимо.

Если campaign detail должен показывать selection, backend response также расширяется
screen-oriented selection projection; React не восстанавливает selection из recipients.

## Routes

```text
/campaigns
/campaigns/new
/campaigns/:campaignId
/campaigns/:campaignId/runs/:runId/{summary|recipients|messages}
```

## Campaign list/detail/create

- URL filters: search/status/channel/scheduled/created/page/size/sort;
- create wizard selects published template version, channel, optional schedule and
  `CampaignSelection` (customerIds/segmentIds/overdue/amount ranges);
- template/channel compatibility comes from backend/catalogue, not hardcoded UI guesses;
- activation and run preparation require confirmation and `CAMPAIGN_MANAGE`;
- selection summary uses bounded counts/projections only.

## Run workflow

```text
create draft -> activate -> prepare run -> eligibility recheck -> delivery monitoring
```

- prepare is single-submit and response navigates to returned run;
- recipient list is server-paged and destinations are already masked;
- recheck result shows eligible/skipped changes and invalidates run/recipients/messages;
- counters are backend-owned and never derived by scanning recipients/messages;
- polling runs only in non-terminal states, pauses in hidden tab and stops in terminal state;
- polling interval has one shared policy and no overlapping requests.

## RBAC and state

- read routes: `CAMPAIGN_READ`;
- create/activate/prepare/recheck/attachment configuration: `CAMPAIGN_MANAGE`;
- hidden actions still handle backend `403`;
- action eligibility is intersection of permission and backend lifecycle state;
- no provider configuration or KumoMTA response is exposed.

## Tests

- campaign URL codec and API contracts;
- create selection serialization and template/channel compatibility;
- permission/action-state matrix;
- prepare double-submit and navigation;
- direct run URL reload through new detail endpoint;
- eligibility invalidation graph;
- bounded polling start/pause/stop tests;
- recipient masking and no row fan-out test.

## Implementation order

1. Backend run detail endpoint + OpenAPI/tenant/security tests.
2. Campaign DTO/API/query keys and list.
3. Create/detail/lifecycle commands.
4. Run summary/recipients/recheck.
5. Polling and end-to-end MSW scenarios.

## Не входит

Provider settings, manual message retry, real-time WebSocket, frontend recipient
materialization and arbitrary audience query DSL.

## Definition of Done

- deep-linked run is independently loadable;
- counters and eligibility remain backend authoritative;
- permission/lifecycle gating and polling are deterministic;
- frontend and backend required CI checks are green.
