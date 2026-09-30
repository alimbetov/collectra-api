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


## CAP-01..CAP-05 deep pass — evidence update

### CAP-01 Auth/session — PARTIAL verification

Frontend contains the slug-login client/context and authenticated route guards. Full registration/session-security coverage remains to be traced in the next auth-specific evidence pass; no defect is asserted from file presence alone.

### CAP-02 Customer master data — VERIFIED implementation gates, test evidence pending exact-SHA run

Current code proves the previously documented implementation gates:
- customer update/status commands carry optimistic `version`;
- email/phone patch carries optimistic `version`;
- manager assignment is validated against an active tenant membership;
- list projection resolves manager/contacts/segments server-side rather than requiring row fan-out.

No A01 defect is opened for those historical gates.

### CAP-03 Contract lifecycle — implementation present, executable verification pending

Backend controller/service/domain and frontend list/detail/form/lifecycle modules are present. Final VERIFIED state requires exact-SHA tests and route-level capability evidence.

### CAP-04 Invoice/receivable — implementation gates VERIFIED, executable verification pending

Backend exposes tenant-scoped invoice list/detail/create/allocation history. Money is represented at the public API boundary with `DecimalString`; frontend models also use `DecimalString`. Customer/contract labels exist in list projection. No lossless-money defect is opened.

### CAP-05 Payments/allocation — PARTIAL / DEFECT A01-004

Backend capability is materially implemented:
- payment create/list/detail;
- allocation history;
- client-supplied `commandId` idempotency;
- payment then invoice locking;
- customer/currency/balance invariants;
- versioned reversal.

However, the tenant React product does not expose the documented P7 workflow. The current router contains invoice routes only under `/receivables`, and the receivable frontend entity exposes invoice operations plus invoice allocation history, but no payment list/detail/create/allocation/reversal client surface. There are no Payment pages in `frontendweb/src/pages/receivables`.

**Impact:** an ordinary tenant user cannot complete payment registration/allocation/reversal through the product UI even though the backend supports it. This breaks the documented receivable lifecycle and prevents a complete UI Golden Journey.

**Classification:** P1 product capability gap.

**Required remediation:**
1. add payment DTO/API/query/mutation frontend boundary;
2. add payment list, create and detail routes/pages;
3. implement allocation command with one stable UUID `commandId` per user intent;
4. implement versioned reversal with conflict reload;
5. link payment allocation rows to invoice detail and invoice allocation rows back to payment detail;
6. permission-gate read/manage actions;
7. add API contract, route and page tests;
8. include P7 in the A02 Golden Journey.
