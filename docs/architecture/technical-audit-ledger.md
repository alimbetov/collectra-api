# Collectra Technical Architecture Audit Ledger

Status: ACTIVE — repository-wide audit  
Started: 2026-09-30  
Audit branch: `fix/pre-channel-release-gate`

## 1. Audit objective

This ledger records the technical audit of every material Collectra layer. It is intentionally evidence-driven: old roadmap status, test existence or source inspection alone does not prove production readiness.

Allowed audit states:

- `IMPLEMENTED` — material implementation exists;
- `PARTIAL` — implementation exists but a known contract/gap remains;
- `AUDIT_REQUIRED` — implementation exists but the current audit has not completed;
- `RELEASE_BLOCKED` — a known defect or missing release evidence blocks production qualification;
- `VERIFIED` — required evidence is green on the exact release candidate.

## 2. Layer inventory

| Layer | Current evidence | Audit state | Required audit focus |
|---|---|---|---|
| Platform / tenant lifecycle | identity, tenant, platform modules | AUDIT_REQUIRED | tenantless/platform boundary, bootstrap, lifecycle |
| Authentication / RBAC | identity module, RBAC docs/tests | AUDIT_REQUIRED | IDOR, nested resources, refresh/replay, permission coverage |
| Service clients | integration/service-client UI + backend | AUDIT_REQUIRED | credential lifecycle, rotation, scopes, tenant isolation |
| Integration Setup Center | integration pages/entities/APIs | IMPLEMENTED | end-to-end setup usability/readiness |
| Integration Sources | integration module + UI | AUDIT_REQUIRED | lifecycle, source ownership, readiness |
| Schema Studio | Source Schema pages/APIs | AUDIT_REQUIRED | versioning, compatibility, validation |
| Mapping Studio | Mapping Profile pages/APIs | AUDIT_REQUIRED | version/publish, deterministic mapping, field/rule safety |
| Production ingestion | ingestion + diagnostics + acceptance tests | PARTIAL | replay, partial failure, late data, transactional boundaries |
| Imports / diagnostics | importing module + FW10-era UI | AUDIT_REQUIRED | masking, retry/replay, bounded polling, support UX |
| Customer / contacts | customer module + frontend | AUDIT_REQUIRED | tenant isolation, contact validity, edit concurrency |
| Contracts | contract module + frontend | AUDIT_REQUIRED | validity, ownership, paging |
| Receivables | invoice/payment/allocation | PARTIAL | allocation/reversal invariants, lock order, collection consistency |
| Collections | case/action/promise/dispute | RELEASE_BLOCKED | current lifecycle defects are being remediated and reverified |
| Campaigns | campaign module + frontend | AUDIT_REQUIRED | audience snapshot, activation, idempotency, eligibility |
| Eligibility | campaign current-state checks | PARTIAL | paid/inactive/no-contact/cancelled edge cases |
| Template Studio | template module + template pages/editor | AUDIT_REQUIRED | draft/version lifecycle, preview, variables, assets, publish immutability |
| Document generation | document module | AUDIT_REQUIRED | deterministic render, font/assets, retry/recovery |
| File Registry / storage | file module + UI | AUDIT_REQUIRED | auth, retention, delete terminal states, object-store failures |
| Message materialization | communication/campaign integration | AUDIT_REQUIRED | immutable snapshots, exactly-one creation |
| Attachments / document links | communication/file/document | AUDIT_REQUIRED | required readiness gate, failure/no-delivery invariant |
| Delivery intent | communication module | AUDIT_REQUIRED | exactly-once logical intent under concurrency |
| Transactional outbox | shared outbox | AUDIT_REQUIRED | claim/recovery, publisher confirms, DEAD handling |
| RabbitMQ topology | messaging configuration/tests | AUDIT_REQUIRED | restart/redelivery/DLQ/HA operational evidence |
| Delivery worker | MessageDeliveryWorker | AUDIT_REQUIRED | provider failure taxonomy and crash points |
| Delivery recovery | MessageRecoveryService/state service | AUDIT_REQUIRED | ambiguous acceptance, no blind resend |
| Provider adapters | provider-neutral gateway + KumoMTA work | RELEASE_BLOCKED | real provider certification remains outside pre-channel gate |
| Communication monitoring | message APIs/UI/metrics | AUDIT_REQUIRED | operator support, masking, stuck/retry/dead visibility |
| Financial analytics | VC9 projection/query services | PARTIAL | range-query remediation, stale READY/late-arrival correctness |
| Communication reporting | reporting projections | AUDIT_REQUIRED | equality, coverage, tenant/range semantics |
| Dashboard | backend/frontend dashboard | AUDIT_REQUIRED | authoritative metrics, empty/error/loading states |
| Audit trail | audit module | AUDIT_REQUIRED | actor/event completeness, tamper/retention considerations |
| Localization | localization module | AUDIT_REQUIRED | locale fallback and template interaction |
| OpenAPI compatibility | baseline + CI tests | IMPLEMENTED | confirm current public contract coverage |
| Frontend architecture | React application | AUDIT_REQUIRED | route completeness, API contracts, recovery UX |
| CI/test architecture | GitHub CI + Testcontainers | PARTIAL | exact-SHA final green, flake/reproducibility, coverage |
| Liquibase/schema | 001..052 migrations | AUDIT_REQUIRED | clean bootstrap, upgrade path, constraints/indexes |
| Production configuration | application-prod.yml | PARTIAL | fail-fast secrets/config and environment matrix |
| Observability | Actuator/Micrometer/tracing/runbooks | PARTIAL | alerts/SLOs/exporters/log aggregation |
| Deployment/IaC | no canonical production deployment package found in repo | RELEASE_BLOCKED | Kubernetes/Helm or documented external infra ownership |
| Backup/restore/DR | no canonical complete evidence identified yet | RELEASE_BLOCKED | PostgreSQL/object store/RabbitMQ RPO/RTO and restore drills |
| Performance/capacity | focused optimizations/tests | RELEASE_BLOCKED | load, soak, sizing and capacity evidence |
| Release qualification | PC-01..PC-20 gate | RELEASE_BLOCKED | PC-19 and one exact-SHA PC-20 evidence |

## 3. Confirmed findings in current audit

### TA-001 — documentation drift

`frontendweb/README.md` still describes FW0 as the current frontend slice even though the repository contains many later business pages/entities. `docs/specs/README.md` also contains historical readiness labels that no longer represent the current implementation.

Disposition: update canonical entry points and explicitly mark historical plans as historical rather than silently deleting useful design history.

### TA-002 — Promise-to-Pay lifecycle dispatch defect

The fulfill/break/cancel application calls resolved to an unsupported overload. Current audit branch removes the dead overload and adds lifecycle regression coverage.

State: remediation pending exact-SHA verification.

### TA-003 — false PAID collection closure

A collection case could be closed with reason PAID without proving that the receivable had zero outstanding balance. Current audit branch adds the financial invariant and regression coverage.

State: remediation pending exact-SHA verification.

### TA-004 — financial analytics query amplification

Financial analytics performed projection readiness/routing per day, causing query count to grow with requested range. Current audit branch is replacing this with range-based projected/raw routing.

State: remediation and PostgreSQL verification in progress.

## 4. Documentation maintenance policy

Every material subsystem must have:

1. one current architecture/contract entry point;
2. lifecycle/state invariants where stateful;
3. tenant/security boundary;
4. transaction/idempotency/concurrency rules where applicable;
5. operational failure/recovery semantics;
6. executable evidence references;
7. explicit production limitations.

Historical specs are retained for traceability but must be labelled historical when superseded.

## 5. Audit execution order

```text
A. system/configuration studios
B. identity/security/tenant isolation
C. ingestion/import/files
D. receivables/collections financial consistency
E. campaigns/templates/documents
F. message/outbox/RabbitMQ/delivery recovery
G. analytics/reporting
H. frontend/operator workflows
I. schema/migrations/configuration
J. observability/deployment/DR/performance
K. exact-SHA release qualification
```

Findings are fixed when they are confirmed and regression evidence is added. A later commit invalidates final VERIFIED status until the applicable release evidence is rerun.
