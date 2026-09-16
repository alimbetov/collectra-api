# FW10 — Imports and mapping configuration

Status: DRAFT / RECORD-DIAGNOSTICS GAP

Depends on: FW2 closure, backend durable import diagnostics

Suggested branches: `feat/import-record-errors-api`,
`feat/frontendweb-fw10a-import-execution`, `feat/frontendweb-fw10b-import-configuration`

## Цель

Реализовать idempotent file/JSON/XML import, history/progress/diagnostics and separate
configuration workspace for source schemas and mapping profiles.

## Backend baseline

- import create: multipart, JSON and XML;
- `GET /api/v1/import-batches/{batchId}`;
- history/detail/current `/api/v1/imports/{importId}/errors`;
- source schema definitions/versions/fields/row config/lifecycle;
- mapping profile definitions/versions/rules/test/lifecycle;
- authorization through import/source/mapping permission policies.

Current `/imports/{id}/errors` synthesizes at most one generic batch failure from
`import_batches.error_code`. It is not record/field diagnostics.

## Required backend closure

Implement durable, tenant-scoped paged diagnostics:

```text
import_error(id, tenant_id, import_id, record_number, field_key,
             code, safe_detail, masked_source_value, created_at)
GET /api/v1/imports/{importId}/errors?page&size
```

Requirements: bounded text, PII masking, stable `(record_number, field_key, id)` ordering,
index `(tenant_id, import_id, id)`, retention tied to import/source file, atomic/batched
persistence semantics and PostgreSQL/security/paging tests. The current batch-level error
remains a summary, not a replacement.

## Routes

```text
/imports
/imports/new
/imports/:importId
/imports/:importId/errors
/imports/source-schemas/*
/imports/mapping-profiles/*
```

Execution and configuration are separate feature modules and query-key namespaces.

## FW10A — execution

- choose source type and published mapping/template versions;
- upload file or submit bounded JSON/XML payload;
- generate one `Idempotency-Key` per logical submission and reuse it on replay;
- progress page polls only pending states and stops on terminal state/hidden tab;
- reload/deep link restores state through batch/history APIs;
- generated document links use backend IDs/routes;
- error table is server-paged and never parses/scans the source file in browser.

## FW10B — configuration

- source schema definition/version editor with fields and row configuration;
- mapping profile/version/rule editor;
- validate and bounded test file/single-rule actions;
- publish/reopen/archive lifecycle with permission/state guards;
- published versions are immutable; editing creates/reopens an allowed draft;
- unsaved navigation confirmation and stale-response protection apply.

## Error/idempotency semantics

- the same idempotency key is never reused for a changed file/body/config selection;
- network ambiguity offers explicit replay with the original key;
- `replayed=true` is visible but not treated as a new batch;
- validation errors map to form fields; record errors stay in paged diagnostics;
- source values and details follow backend masking and are not logged.

## Tests

- idempotency-key stability and changed-intent regeneration;
- multipart/JSON/XML API contract tests;
- polling start/pause/terminal tests;
- reload/deep-link recovery;
- paged durable record/field diagnostics and masking;
- source/mapping lifecycle and published immutability;
- permission and invalidation matrices.

## Implementation order

1. Durable backend diagnostics and OpenAPI update.
2. Import execution DTO/API/query keys and submission.
3. History/progress/errors/generated outputs.
4. Source schema configuration.
5. Mapping configuration/test/lifecycle.

## Не входит

Unbounded browser parsing, client-side ETL, arbitrary scripting, local persistence of
uploaded content and silent automatic replay with a new key.

## Definition of Done

- ambiguous submit can be safely reconciled/replayed;
- record/field failures are durable, paged and masked;
- configuration lifecycle cannot mutate published versions silently;
- backend/frontend CI and import scenario tests are green.
