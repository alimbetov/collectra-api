# A01 — Product Capability Coverage Audit

Baseline SHA: `76e121082d3f339c8440fa2d9f34a092b3048345`
Status: IN_PROGRESS

## Objective

Prove that each product capability is usable as a complete tenant/user outcome, not merely represented by a controller, table, React page, or historical specification.

## Traceability dimensions

Every capability is checked across:

1. accepted product requirement / user process;
2. backend application/domain behavior;
3. REST contract;
4. persistence;
5. frontend route/page/query/mutation;
6. authorization/tenant boundary;
7. automated verification;
8. operational/recovery support where asynchronous.

A capability is VERIFIED only when all mandatory dimensions are evidenced.

## Product capability baseline

Derived from the current README core flow, frontend user processes P1-P15, screen/API matrix, and current source tree.

| ID | Capability | Initial state | Notes |
|---|---|---|---|
| CAP-01 | Tenant registration, login and session lifecycle | VERIFYING | P1/P2 |
| CAP-02 | Customer master data, contacts and segmentation | VERIFYING | P3 |
| CAP-03 | Contract lifecycle | VERIFYING | P4 |
| CAP-04 | Invoice/receivable creation and monitoring | VERIFYING | P5/P6 |
| CAP-05 | Payment registration, allocation and reversal | VERIFYING | P7 |
| CAP-06 | Collection case workflow, promise, dispute, actions, timeline | VERIFYING | P8 |
| CAP-07 | Template authoring, validation, preview and publication | VERIFYING | P9 |
| CAP-08 | Campaign creation, audience, preparation and execution | VERIFYING | P10/P11 |
| CAP-09 | Message delivery and monitoring | VERIFYING | P12 |
| CAP-10 | File/document lifecycle and business linkage | VERIFYING | P13 |
| CAP-11 | Tenant administration/RBAC | VERIFYING | P14 |
| CAP-12 | Profile/security/session management | VERIFYING | P15 |
| CAP-13 | Source schema + mapping configuration | VERIFYING | integration setup |
| CAP-14 | JSON/XML/CSV/XLSX ingestion/import and diagnostics | VERIFYING | synchronous + async paths |
| CAP-15 | Service clients / machine-to-machine integration | VERIFYING | integration setup |
| CAP-16 | Dashboard operational projections | VERIFYING | FW03 |
| CAP-17 | Tenant analytics/reporting | VERIFYING | VC-9 evidence required |
| CAP-18 | Platform administration | VERIFYING_SCOPE | explicitly excluded from tenant frontend MVP in historical matrix |
| CAP-19 | External provider delivery | VERIFYING_SCOPE | simulation/mock and real adapter evidence must be distinguished |
| CAP-20 | Operational recovery/diagnostics visible to authorized users/operators | VERIFYING | async product usability |

## Historical gates that require current-code proof

The current screen/API matrix records post-freeze gates. A01 treats these as open verification questions until code/tests on the baseline prove closure:

- lossless money transport for dashboard/receivables/collections;
- expected-version customer/contact updates and bounded manager selection;
- customer/contract labels and bounded allocation history;
- collection next-action filtering/sorting and bounded child history;
- direct campaign/run detail and idempotent run preparation;
- template direct detail, optimistic revision and builder limits;
- durable record/field import diagnostics;
- paged file registry and safe public DTO;
- paged identity lists, explicit membershipId and role revision.

## Verification method

For every CAP item produce a row-level evidence set:

`requirement -> frontend -> endpoint -> service/domain -> repository/migration -> permission -> test`.

Async capabilities additionally require:

`enqueue/outbox -> worker -> terminal state -> retry/recovery -> user/operator visibility`.

## Exit criteria for A01

A01 can close only when:

- every CAP item is VERIFIED or explicitly OUT_OF_SCOPE;
- every PARTIAL/MISSING/BROKEN item has a defect ledger entry;
- P0/P1 capability gaps are remediated and tested;
- the Golden Journey prerequisites required by A02 are enumerated;
- evidence points to the exact audited SHA.
