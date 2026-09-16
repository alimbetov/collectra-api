# FW9 — Templates and builder

Status: REVIEWED / DETAIL, REVISION AND BOUNDS GATE

Depends on: FW2 closure

Suggested branches: `feat/frontendweb-fw9a-template-management`,
`feat/frontendweb-fw9b-template-builder`

## Цель

Реализовать management lifecycle шаблонов/версий и bounded template builder с
validate/preview/publish, assets и неизменяемостью published versions.

## Backend contract and permissions

- `/api/v1/templates...`: `TEMPLATE_READ`, `TEMPLATE_MANAGE`, `TEMPLATE_PUBLISH`;
- `/api/v1/template-builder/catalog|validate|preview|documents|versions|assets`;
- field catalogue: `FIELD_READ/CREATE/UPDATE`;
- presets: read/preview/apply under template permissions.

Management and builder DTO stay in separate modules. Catalogue owns available fields,
channels, assets and syntax/schema version.

### Required backend closure

- add tenant-scoped `GET /api/v1/templates/{templateId}`; current API has only paged list,
  so detail cannot restore its header after reload without scanning pages;
- expose JPA optimistic revision separately from business `templateVersion` number and
  require it (or `If-Match`) for rename, draft update and lifecycle transitions;
- define maximum HTML/stylesheet/builder JSON/preview payload sizes and reject oversized
  requests with stable `413`/ProblemDetail semantics;
- asset/catalog lists need tenant limits or paging. Current asset list is unbounded, even
  though each individual inline asset is restricted to 2 MB.

## Routes

```text
/templates
/templates/new
/templates/:templateId
/templates/:templateId/versions/:versionId
/templates/:templateId/versions/:versionId/builder
/templates/assets
/templates/fields
```

## FW9A — management

- server-paged template list with fixed filters;
- detail shows versions and lifecycle status;
- create/update/archive actions are permission/state gated;
- validate and preview show structured diagnostics;
- publish requires confirmation and invalidates template/version/campaign selection data;
- published content is never edited in place: UI creates/reopens a draft according to
  backend command contract.
- `templateVersion` is the business ordinal and `revision` is the concurrency token; UI
  never overloads one field for both meanings.

## FW9B — builder

- load catalogue once per compatible schema version and use stable query key;
- editor keeps draft form model separate from transport document;
- preview/validate is explicit or safely debounced with cancellation; responses from older
  requests cannot replace newer input result;
- save serializes only supported schema and preserves backend version identifiers;
- update/publish sends expected revision and handles stale `409` without overwriting;
- assets upload/delete through backend only; object-storage URLs are never constructed;
- unsaved navigation is confirmed;
- payload and preview size remain bounded; no production-scale document rendering loop.

## Concurrency and lifecycle

- pending publish/save cannot be double-submitted;
- lifecycle `409` refreshes authoritative version and preserves local draft separately;
- preview is non-authoritative and never marks a version valid/published locally;
- campaign selection accepts only backend-approved published compatible versions.

## Tests

- permission/lifecycle action matrix;
- published immutability scenario;
- catalogue/schema compatibility and unknown component fallback;
- stale preview response cancellation/order test;
- save/validate/publish invalidation graph;
- asset upload/delete and safe URL behavior;
- direct template detail reload and revision conflict tests;
- payload-size and asset/catalog bound tests;
- unsaved-navigation and keyboard builder basics.

## Implementation order

1. Backend direct detail, revision preconditions and payload/catalog bounds.
2. Management DTO/API/query keys and template/version list/detail.
3. Lifecycle commands and preview shell.
4. Builder catalogue and typed document model.
5. Editor/validate/preview/save.
6. Assets/presets and campaign integration.

## Не входит

Arbitrary executable template code, client-side PDF authority, provider-specific editor,
collaborative editing and automatic migration between incompatible schema versions.

## Definition of Done

- published versions cannot be silently mutated;
- stale drafts cannot overwrite a newer revision and detail reload is list-independent;
- stale previews cannot overwrite current input;
- builder remains catalogue/schema driven and provider neutral;
- permission, lifecycle and frontend CI scenarios are green.
