# Top 30 Weak Zones — Risk-Based Functional Hardening

Status: NORMATIVE SMOKE BACKLOG
Branch: spec/functional-hardening-user-journeys
Scope: VC-0..VC-8; VC-9 excluded

## Selection method

A weak zone is a boundary where an isolated component test can pass while the product still fails as a user/business system. Ranking prioritizes: tenant/auth compromise; incorrect financial state; duplicate/incorrect delivery; Golden Journey blockage; unrecoverable concurrency; misleading frontend state.

Each WZ row is a smoke contract, not a claim that a defect exists.

| Rank / ID | Weak zone | Failure to detect | Required smoke invariant | Evidence already present | Gap / required coverage |
|---|---|---|---|---|---|
| 1 WZ-01 | Cross-tenant object access | Tenant A reads/mutates Tenant B | path/query/body foreign IDs never disclose or mutate B | domain isolation tests + FunctionalHardeningSecuritySmoke | expand to all tenant-owned resources |
| 2 WZ-02 | Foreign-reference injection | A creates object linked to B customer/template/file/etc | every referenced aggregate re-scoped to authenticated tenant | partial domain tests | parameterized body-reference matrix |
| 3 WZ-03 | Vertical authorization | restricted human calls hidden mutation directly | server permission, not UI, controls read/manage | RoleAccess + FH characterization | resolve D01 then 403 matrix |
| 4 WZ-04 | Human/service/platform trust-zone confusion | wrong principal enters another zone | ROLE_SERVICE, ROLE_HUMAN, PLATFORM remain disjoint | security/service-client tests | global endpoint-zone smoke |
| 5 WZ-05 | Stale authorization/credential | removed permission or rotated secret remains usable | authorization version/session/client lifecycle invalidates old access | TenantSecurityManagement, ServiceClientSecurity | join into persona smoke |
| 6 WZ-06 | Collections UI/backend split | collection backend exists but user cannot work cases | /collections queue/detail/lifecycle usable end-to-end | backend collection tests/spec | implement D02 + browser smoke |
| 7 WZ-07 | Collection vs finance state coupling | case/promise changes invoice truth incorrectly | collection workflow never invents payment status/outstanding | collection/receivable tests | cross-domain invariant smoke |
| 8 WZ-08 | Payment allocation correctness | over/foreign/wrong-currency allocation corrupts balance | customer/currency/amount invariants + authoritative totals | CustomerReceivableCore | adversarial API matrix |
| 9 WZ-09 | Allocation idempotency/reversal | replay double-allocates or reversal drifts totals | command replay single effect; reversal restores exact authoritative balances | receivable tests | API+DB replay/reversal smoke |
| 10 WZ-10 | Money/date transport | frontend rounds money or disagrees on overdue business date | decimal strings lossless; backend businessDate authoritative | DecimalString + frontend receivable models | browser/API boundary smoke |
| 11 WZ-11 | Ingestion idempotency | retry duplicates customer/invoice/payment | same key+same body single logical effect; changed body conflicts | ProductionIngestionAcceptance | Golden Journey replay smoke |
| 12 WZ-12 | Mapping/schema canonicalization | accepted source data maps to wrong canonical field/type | published schema/mapping deterministically produces expected canonical record | mapping/source tests | full source→business assertion |
| 13 WZ-13 | Import partial diagnostics | bad row hides/corrupts good rows or leaks sensitive input | durable per-record outcomes; safe diagnostics; documented atomicity | import postgres/masking tests | user-facing import smoke |
| 14 WZ-14 | Service-client lifecycle | blocked/rotated client continues ingestion | old secret/JWT unusable; scopes enforced | ServiceClientSecurity | ingestion endpoint continuation test |
| 15 WZ-15 | Customer 360 consistency | screens show stale/conflicting cross-domain data | customer links resolve authoritative contacts/contracts/receivables/collections | customer API/page tests | D05 + cross-domain deep-link smoke |
| 16 WZ-16 | Optimistic concurrency | stale customer/contract/case edit silently overwrites | expected version conflict → 409 → reload/reconcile; no auto replay | contract/customer/collection specs/tests | browser reconciliation smoke |
| 17 WZ-17 | Eligibility race | paid customer still receives debt reminder | eligibility recheck converts paid recipient to SKIPPED/PAID | CampaignStabilizationIntegrationTest | include in Golden Journey |
| 18 WZ-18 | Audience tenant leakage | campaign selects foreign customer/segment | audience candidates strictly tenant-scoped | campaign/security coverage partial | Alpha/Beta audience smoke |
| 19 WZ-19 | Template immutability/materialization | published content changes after run or wrong version rendered | message references immutable published version/materialized content | template/materialization tests | campaign→message assertion |
| 20 WZ-20 | Template/content injection | script/expression/control content escapes policy | validation/rendering enforces channel/HTML/placeholder policy | HtmlTemplatePolicy + placeholder tests | HTTP/frontend unsafe-content smoke |
| 21 WZ-21 | File/asset isolation | foreign file attached/read; storage internals leak | FILE permissions + tenant ownership + safe DTO | FileControllerIntegrationTest | foreign asset→template/message smoke |
| 22 WZ-22 | Attachment readiness gate | provider called before required file ready | PENDING/FAILED required attachment means zero provider calls | attachment gate tests | integration delivery smoke |
| 23 WZ-23 | Duplicate broker delivery | same message physically sent/counted multiple times | 100 duplicates/concurrent workers → exactly one physical delivery/counter | Slice10aP08ConcurrencyIntegrationTest | retain as pre-VC9 critical |
| 24 WZ-24 | Retry/counter atomicity | retry changes Message but not CampaignRun or double-counts | transition + counters atomic/idempotent; retryCount semantics exact | worker scenario/state tests | Testcontainers outcome matrix |
| 25 WZ-25 | Ambiguous provider outcome | accepted-but-response-lost blindly resends | ambiguous outcome is explicit, stable delivery key, no unsafe blind resend | normative Slice10A matrix | verify implemented model or decision gap |
| 26 WZ-26 | Recovery of stale PROCESSING | crash strands or double-finalizes message | recovery uses same transition/counter rules and terminal no-op | MessageRecoveryServiceTest | PostgreSQL/RabbitMQ recovery smoke |
| 27 WZ-27 | Outbox claim/publish atomicity | event lost/duplicated under competing publishers | claim ownership atomic; retry safe; duplicate event downstream idempotent | OutboxClaimIntegrationTest | Golden delivery chain integration |
| 28 WZ-28 | Monitoring privacy/truthfulness | raw PII/provider body leaks or UI fabricates history | masked destination, safe error, no credentials/body, attemptCount != history | Slice10aP11 + frontend message tests | browser message detail smoke |
| 29 WZ-29 | Frontend route/API wiring | backend works but primary page/deep link/filter/permission wiring fails | every REAL route loads, deep-links, persists URL state, handles 401/403/404/409 | selected page tests | missing page smoke across partial routes |
| 30 WZ-30 | Clean bootstrap/full-chain drift | migrations/OpenAPI/frontend/backend pass separately but clean system fails | clean DB → bootstrap → Golden Journey → adapter boundary on exact SHA | OpenAPI/Foundation/CI tests | final composite pre-VC9 smoke |

## Architectural grouping

Security boundary: WZ-01..05, 18, 21, 28.
Financial/business truth: WZ-07..10, 15..17.
Integration/data quality: WZ-11..14.
Collections/user workflow: WZ-06, 07, 16.
Content/campaign: WZ-17..22.
Delivery/reliability: WZ-22..27.
Product/browser/release coherence: WZ-29..30.

## Test architecture

Do not create 30 giant duplicated Spring tests. Implement reusable fixtures and parameterized matrices:
- SecurityIsolationSmokeIntegrationTest: WZ-01/02/04/18/21.
- BusinessCoreAuthorizationSmokeIntegrationTest: WZ-03/05 after D01/D03/D06.
- ReceivableCollectionSmokeIntegrationTest: WZ-07/08/09/10/16.
- IntegrationIngestionSmokeIntegrationTest: WZ-11/12/13/14.
- CampaignDeliveryGoldenJourneyIntegrationTest: WZ-17/19/21/22/24/27.
- DeliveryResilienceSmokeIntegrationTest: WZ-23/25/26.
- Frontend browser/page smoke suite: WZ-06/10/15/16/20/28/29.
- PreVc9GoldenJourneySmokeIntegrationTest: WZ-30 and the mandatory cross-domain chain.

Existing focused tests remain authoritative regression tests; smoke suites prove boundaries between domains.

## Evidence rule

A WZ becomes PASS only after its named smoke and affected existing regressions execute successfully on the same verification SHA. D01-D15 are accepted. Only a newly discovered D16+ semantic conflict may yield BLOCKED_DECISION.
