# Slice 10A — Final Closure

Status: READY FOR IMPLEMENTATION
Depends on: test infrastructure cleanup

## 1. Goal

Закрыть Slice 10A не только по JaCoCo, но и по обязательной scenario matrix.

Merge-ready означает одновременно:

```text
mandatory scenario matrix -> GREEN
critical-core LINE        -> >= 95%
critical-core BRANCH      -> >= 90%
full integration suite    -> GREEN
```

## 2. Mandatory closure order

### P08 — duplicate/idempotency/concurrency/ambiguous outcome

Обязательно добавить deterministic tests для:

- 8 workers + 100 duplicate events -> ровно один physical provider call;
- final state `SENT` exactly once;
- `sentCount` increment exactly once;
- `attemptCount == 1` для одного фактического claim;
- worker/retry/recovery three-way races;
- terminal state cannot be reopened;
- fault points:
  - AFTER_CLAIM;
  - BEFORE_PROVIDER;
  - AFTER_PROVIDER_ACCEPTED;
  - BEFORE_STATE_COMMIT;
  - AFTER_STATE_COMMIT.

Production design MUST define behavior для `ACCEPT_THEN_TIMEOUT`. Blind resend без delivery ledger/idempotency contract запрещён.

### P11 — security abuse

Добавить resource-family matrix:

- anonymous;
- wrong role;
- service JWT in human zone;
- forged tenantId in body/path/query/header;
- cross-tenant BOLA/IDOR;
- disabled/stale user;
- stale authorization cache;
- oversized page/filter/request;
- SQL/filter injection;
- CRLF/log injection;
- sensitive data redaction;
- mock/fault controls disabled in production profile;
- provider endpoint cannot be sourced from user-controlled data.

### P02 — parser/file abuse

Обязательно:

- deterministic JSON/XML/CSV/XLSX matrix;
- XXE rejection;
- Billion Laughs/entity expansion rejection;
- CSV/XLSX formula injection policy;
- MIME mismatch;
- oversized file/payload;
- duplicate replay/idempotency;
- tenant-scoped uniqueness.

### P10 — API/observability/log safety

Обязательно:

- API -> DB -> metric consistency;
- bounded metric label cardinality;
- correlation/trace continuity through retry;
- raw body/auth header/PII absent from logs;
- alert generation;
- alert de-duplication;
- stable paging/sorting/filter combinations.

## 3. Coverage gate

Existing Maven profile `slice10a-coverage` remains normative.

Critical classes:

- `MessageDeliveryWorker`
- `MessageStateService`
- `MessageRecoveryService`
- `MessageRetryPolicy`
- `MessageAttachmentContentResolver`
- `Message`
- `MessageAttachment`
- `CampaignRun`

Required:

```text
LINE   >= 0.95
BRANCH >= 0.90
```

CI MUST execute:

```bash
mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage
```

Plain `mvn clean verify` alone is not the Slice 10A merge gate.

## 4. Documentation closure

После green gate обновить:

- `slice-10a-coverage-audit.md`;
- Slice 10A status;
- scenario matrix references;
- list of resolved BLOCKED design decisions;
- known residual non-blocking risks.

## 5. Definition of Done

Slice 10A может быть помечен COMPLETED только если:

- no mandatory RED rows;
- no unresolved mandatory BLOCKED rows;
- 95/90 critical-core gate green;
- full integration suite green;
- CI запускает coverage profile;
- documentation matches actual implementation.
