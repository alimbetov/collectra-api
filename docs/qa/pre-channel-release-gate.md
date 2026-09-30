# Pre-Channel Release Gate

Status: ACTIVE — FAIL-CLOSED
Historical baseline: `main@ec6d1ac2b9ac9035ded57de3c3825c011e946d2c`
Current reconciliation baseline: `main@9b3369d52af5bfaf0436019b4b8eb97e51f0b956`
Active qualification evidence: main CI run #2801 (`36713091538`) is in progress; no PC row is promoted to `VERIFIED` until that exact-SHA run is terminal green.

## 1. Purpose

This gate qualifies the complete Collectra business flow up to the external delivery-provider boundary. It does not certify KumoMTA, SMS, Telegram, WhatsApp or any other real provider.

A row is `VERIFIED` only when its executable evidence is green on the exact release-candidate SHA. Test existence, source inspection, an older green run or a green frontend-only job is insufficient.

Allowed states:

`NOT_STARTED | IMPLEMENTED | TESTED | VERIFIED | BLOCKED`

## 2. Boundary

```text
Integration source
 -> schema/mapping/validation
 -> persisted Customer / Invoice / Payment
 -> Collection lifecycle
 -> Campaign audience + eligibility
 -> immutable published TemplateVersion
 -> Message materialization
 -> generated document / required attachment gate
 -> exactly-one delivery intent
 -> PostgreSQL Outbox
 -> RabbitMQ
 -> MessageDeliveryWorker / provider-neutral DeliveryGateway
 ---- external provider boundary ----
 -> KumoMTA / SMS / Telegram / WhatsApp
```

The release gate stops before a live provider. Deterministic simulated adapters are allowed only to prove the provider-neutral worker contract.

## 3. Gate ledger

| ID | Required invariant | Primary executable evidence | State |
|---|---|---|---|
| PC-01 | Supported ingestion persists canonical business records; replay is idempotent | `ProductionIngestionAcceptanceTest`, `BusinessRecordPersistenceIntegrationTest` | TESTED |
| PC-02 | Customer, invoice, payment and allocation remain money/currency/tenant consistent | `RabbitMqMessageDeliverySmokeIntegrationTest`, receivable integration tests | TESTED |
| PC-03 | Collection lifecycle is coherent with receivable state | `CollectionLifecycleApiIntegrationTest`, `CollectionHistoryApiIntegrationTest` | TESTED |
| PC-04 | Only valid published template snapshots participate in a run | `CampaignMessageMaterializationIntegrationTest`, template contract tests | TESTED |
| PC-05 | Campaign activation/preparation validates configuration and audience | `CampaignContractClosureIntegrationTest`, `CampaignStabilizationIntegrationTest` | TESTED |
| PC-06 | Campaign recipient/audience snapshot is deterministic and tenant-scoped | `GenericCustomerCampaignAudienceIntegrationTest` | TESTED |
| PC-07 | Eligibility is rechecked against current customer/contact/receivable state | campaign stabilization/materialization tests; strengthen negative golden journey | TESTED |
| PC-08 | Message materialization snapshots destination, locale, template and rendered content | `CampaignMessageMaterializationIntegrationTest` | TESTED |
| PC-09 | Required generated attachment/link blocks delivery until durable READY; terminal required failure blocks delivery | attachment/link integration and state-gate tests | TESTED |
| PC-10 | A message can acquire one logical delivery intent only; concurrent/duplicate completion cannot duplicate it | delivery-request/attachment concurrency tests; strengthen composite smoke | TESTED |
| PC-11 | Delivery intent and outbox event are transactionally durable and recoverable | outbox integration tests | TESTED |
| PC-12 | Outbox traverses real RabbitMQ topology to provider-neutral worker | `RabbitMqMessageDeliverySmokeIntegrationTest` | TESTED |
| PC-13 | Duplicate HTTP/broker/generation/recovery work is logically idempotent | reservation concurrency, outbox claim, message/recovery tests | TESTED |
| PC-14 | Transient/permanent/ambiguous failures follow bounded safe recovery; no blind resend after uncertain acceptance | worker scenario matrix + recovery tests; production ambiguity evidence required | TESTED |
| PC-15 | Tenant/RBAC boundaries hold through API, nested references and async resources | `FunctionalHardeningSecuritySmokeIntegrationTest`, tenant isolation tests | TESTED |
| PC-16 | Operator-facing routes/actions expose business state without requiring infrastructure knowledge | frontend route/page tests + functional UX audit | TESTED |
| PC-17 | Empty PostgreSQL bootstrap and supported upgrade produce a usable schema/application | `CleanBootstrapIntegrationTest`; upgrade evidence required | TESTED |
| PC-18 | One true Golden Journey proves ingestion-created business objects flow through collection/campaign to RabbitMQ boundary and both analytics projections | `RabbitMqMessageDeliverySmokeIntegrationTest` | IMPLEMENTED |
| PC-19 | Negative/failure evidence proves ineligible, failed, duplicate and foreign-tenant paths cannot create forbidden delivery side effects | A02 F01-F14 mapped integration/scenario tests | IMPLEMENTED |
| PC-20 | All applicable PC evidence is green on one exact SHA with PostgreSQL + RabbitMQ boundaries | CI release-candidate run | NOT_STARTED |

No row may move to `VERIFIED` without exact-SHA evidence.

## 4. PC-18 Golden Journey acceptance

The smoke MUST prove object provenance, not recreate business objects after ingestion:

```text
service client
 -> CUSTOMER ingestion -> persisted Customer
 -> INVOICE ingestion  -> persisted overdue Invoice
 -> PAYMENT ingestion  -> persisted Payment
 -> idempotent allocation -> outstanding reduced
 -> Collection Case + action
 -> Campaign prepare
 -> current-state eligibility
 -> Message QUEUED
 -> exactly one delivery intent
 -> Outbox
 -> real RabbitMQ topology
 -> provider-neutral worker
```

Required assertions include tenant IDs, external IDs from source payloads, invoice outstanding amount, campaign/message references and one logical delivery request.

## 5. PC-19 Negative / Failure Journey acceptance

PC-19 consumes the A02 failure matrix rather than requiring one artificial monolithic negative test. A composite test may be added when it proves a cross-boundary seam that isolated tests cannot prove. At minimum:

```text
fully paid invoice      -> no eligible delivery
inactive customer       -> no eligible delivery
missing/disabled contact-> no eligible delivery
required attachment fail-> Message FAILED, no delivery request
foreign tenant IDs      -> non-disclosing denial, zero side effect
duplicate completion    -> no duplicate delivery intent
duplicate broker event  -> no duplicate logical provider attempt
```

Each denial must assert both state and absence of forbidden durable side effects.

## 6. Release evidence rules

1. PostgreSQL-dependent invariants use PostgreSQL/Testcontainers.
2. RabbitMQ-dependent invariants use real RabbitMQ topology, not direct listener invocation.
3. Async proof polls durable observable state with bounded timeout.
4. No arbitrary sleep is acceptance evidence.
5. A later code commit invalidates final evidence until rerun.
6. Formatting, compile, unit, integration, frontend and smoke gates must all pass on the same candidate SHA.
7. A real channel adapter is out of scope until PC-01..PC-20 are VERIFIED or an explicit documented exception is accepted.


## 7. A02 traceability

A02 is the cross-boundary assurance scenario catalogue; this document is the release qualification ledger that consumes that evidence. They are not independent sources of truth.

| Release gate | A02 evidence consumed | Qualification meaning |
|---|---|---|
| PC-18 | G01-G18, with the canonical positive chain centered on `RabbitMqMessageDeliverySmokeIntegrationTest` | positive business chain reaches provider-neutral terminal delivery and analytics without reconstructing post-ingestion business objects |
| PC-19 | F01-F14 plus explicit paid/inactive/no-contact/required-attachment/foreign-tenant/duplicate-side-effect assertions | failures and denials are durable, tenant-safe and create no forbidden side effects |
| PC-20 | all applicable G/F evidence plus repository CI gates | final same-exact-SHA qualification; no separate business behavior is introduced here |

A02 rows may reference focused tests where that is the strongest deterministic proof. PC-18/PC-19 must not duplicate those tests solely to create a second ledger-shaped suite.

## 8. Candidate evidence procedure

1. Record candidate commit SHA.
2. Run the repository CI gates from `.github/workflows/ci.yml`: formatting, focused Slice 7 unit gate, Maven `clean verify -Pslice10a-coverage`, frontend typecheck/tests/build.
3. Confirm that RabbitMQ-backed PC-18 evidence executed in the candidate verification path; if excluded by test discovery/profile, execute and record it explicitly.
4. On any failure: retain raw failure evidence, fix root cause, create a new SHA, and restart qualification.
5. Only when the required evidence is green on the candidate SHA may applicable rows move to `VERIFIED`.

## 9. Current audit finding

The current `RabbitMqMessageDeliverySmokeIntegrationTest` is the canonical PC-18 executable: CUSTOMER/INVOICE/PAYMENT are ingested and persisted, allocation/reversal invariants are exercised, collection case/action is created, campaign/message provenance is asserted, exactly one delivery request is asserted, the outbox traverses real RabbitMQ, the deterministic provider-neutral adapter reaches SENT, and financial plus communication analytics are asserted.

PC-19 consumes the A02 F01-F14 executable failure evidence. PC-20 remains open until all applicable evidence is green together on one exact candidate SHA.
