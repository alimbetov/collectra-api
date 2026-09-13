# Slice 10A — Traceable Coverage Audit

Status: LIVING QUALITY GATE
Branch: `feat/slice-10a-test-lab-consolidated`
Normative sources:
- `docs/specs/slice-10a-multichannel-mock-smoke-lab.md`
- `docs/specs/slice-10a-user-security-scenario-matrix.md`
- `docs/specs/slice-10a-test-users-role-matrix.md`

## Status legend

- `GREEN` — automated test proves the requirement through the intended boundary.
- `YELLOW` — automated coverage exists but does not prove the complete normative scenario.
- `RED` — mandatory scenario is not covered by an automated test.
- `BLOCKED` — production contract/infrastructure is missing or ambiguous, so a correct test cannot yet be written without first changing production code.

A line/branch coverage percentage is not a substitute for scenario coverage. Slice 10A is merge-ready only when the mandatory scenario matrix and the code-coverage gate are both green.

## Current code coverage gate

Maven profile `slice10a-coverage` currently requires `LINE >= 95%` and `BRANCH >= 90%` for:

- `MessageDeliveryWorker`
- `MessageStateService`
- `MessageRecoveryService`
- `MessageRetryPolicy`
- `MessageAttachmentContentResolver`
- `Message`
- `MessageAttachment`
- `CampaignRun`

This is the critical-core coverage gate, not whole-project coverage.

## P01 — Tenant/project/channel configuration

| Requirement | Automated evidence | Status |
|---|---|---|
| tenant/versioned configuration lifecycle | `VersionedConfigurationIntegrationTest` | YELLOW |
| tenant security management | `TenantSecurityManagementIntegrationTest` | YELLOW |
| unauthorized tenant-management access | `RoleAccessSmokeIntegrationTest#tenantUserHasSelfServiceAndReadOnlyTenantAccess`, `#customRoleReceivesOnlyItsSelectedPermission` | GREEN |
| service client cannot enter human/admin zones | `RoleAccessSmokeIntegrationTest#serviceClientIsRejectedByEveryHumanAndPlatformZone` | GREEN |
| per-channel mock/provider mode matrix | none located | RED |
| unknown provider/mode fails closed | none located | RED |
| mock provider forbidden in production | none located | RED |
| concurrent config update/version conflict | partial versioning coverage only | YELLOW |
| secret masking/effective safe configuration view | no explicit scenario test located | RED |
| malformed provider/backoff/rate configuration fail-fast | no explicit scenario test located | RED |

## P02 — Import/API ingestion

| Requirement | Automated evidence | Status |
|---|---|---|
| tabular import batch path | `TabularImportBatchIntegrationTest` | YELLOW |
| business persistence after ingestion | `BusinessRecordPersistenceIntegrationTest` | YELLOW |
| field catalogue/mapping primitives | `FieldCatalogIntegrationTest`, `TemplateMappingDomainIntegrationTest` | YELLOW |
| JSON/XML/CSV/XLSX deterministic format matrix | no single traceable matrix proving all four formats | RED |
| required fields/type/date/decimal/currency boundaries | partial existing import/domain tests | YELLOW |
| duplicate upload/idempotent replay | no explicit replay matrix located | RED |
| tenant-scoped import uniqueness | partial tenant-aware integration coverage | YELLOW |
| XXE rejection | no explicit attack test located | RED |
| entity expansion / Billion Laughs rejection | no explicit attack test located | RED |
| CSV/XLSX formula injection policy | no explicit attack test located | RED |
| oversized payload/file and MIME mismatch | no explicit bounded-abuse matrix located | RED |

## P03 — Template/materialization/placeholders

| Requirement | Automated evidence | Status |
|---|---|---|
| campaign message materialization | `CampaignMessageMaterializationIntegrationTest` | GREEN |
| mapping domain integration | `TemplateMappingDomainIntegrationTest` | YELLOW |
| provider-neutral five-channel snapshot | `MessageScenarioMatrixTest#allChannelsPreserveProviderNeutralSnapshot` | GREEN |
| required materialized fields fail closed | `MessageScenarioMatrixTest#requiredCreationFieldsFailClosed` | GREEN |
| destination/locale/subject length boundaries | `MessageScenarioMatrixTest#snapshotLengthBoundariesAreEnforced` | GREEN |
| complete placeholder namespace matrix | no >=30 traceable placeholder matrix located | RED |
| HTML/script/expression injection handling | no explicit adversarial materialization matrix located | RED |
| RU/KZ/CJK formatting matrix | partial localization/materialization tests | YELLOW |
| immutable materialized version across retry | partial persistence/domain coverage | YELLOW |

## P04 — Campaign lifecycle and eligibility

| Requirement | Automated evidence | Status |
|---|---|---|
| prepare/materialize/run baseline | `CampaignMessageMaterializationIntegrationTest`, `CampaignStabilizationIntegrationTest` | GREEN |
| payment/eligibility stabilization | `CampaignStabilizationIntegrationTest` | GREEN |
| message/run persistence | `CommunicationMessagePersistenceIntegrationTest` | GREEN |
| duplicate/concurrent campaign start | no complete traceable race matrix located | RED |
| pause/resume/cancel lifecycle matrix | partial campaign tests | YELLOW |
| cancel-before-claim / cancel-during-processing races | no explicit deterministic race test located | RED |
| stale UI/API command behavior | no explicit scenario matrix located | RED |
| cross-tenant/insufficient-role campaign operations | partial RBAC/tenant tests, not campaign-specific matrix | YELLOW |

## P05 — Channel routing/provider boundary

| Requirement | Automated evidence | Status |
|---|---|---|
| all five channels traverse provider-neutral worker boundary | `MessageDeliveryWorkerScenarioMatrixTest#everyChannelReachesProviderBoundaryWithoutAdapterSpecificAssumptions` | GREEN |
| accepted/retryable/rate-limit/permanent/provider-auth mapping | `MessageDeliveryWorkerScenarioMatrixTest#providerOutcomeMatrix` | GREEN |
| retry exhaustion boundary | `MessageDeliveryWorkerScenarioMatrixTest#retryExhaustionBoundaryIsExact` | GREEN |
| KumoMTA HTTP client | `KumoMtaClientHttpTest` | GREEN |
| KumoMTA gateway | `KumoMtaEmailDeliveryGatewayTest` | GREEN |
| KumoMTA error classification | `KumoMtaErrorClassifierTest` | GREEN |
| complete deterministic provider outcomes (`TIMEOUT_BEFORE_ACCEPT`, `ACCEPT_THEN_TIMEOUT`, malformed, refusal, slow success, 403 etc.) | partial KumoMTA/worker tests | YELLOW |
| real SMS/WhatsApp/Telegram/In-App adapters | intentionally not implemented for this slice | BLOCKED |

## P06 — Attachments/document generation/storage

| Requirement | Automated evidence | Status |
|---|---|---|
| attachment domain invariants | `MessageAttachmentTest` | GREEN |
| attachment service | `MessageAttachmentServiceTest` | GREEN |
| content resolver | `MessageAttachmentContentResolverTest` | GREEN |
| required attachment delivery gate | `MessageStateServiceAttachmentGateTest` | GREEN |
| attachment failure does not call provider | `MessageDeliveryWorkerScenarioMatrixTest#attachmentFailuresNeverInvokeProvider` | GREEN |
| async generated document path | `AsyncDocumentGenerationIntegrationTest` | GREEN |
| missing/zero/corrupt/MIME/storage-timeout/path-traversal/cross-tenant/oversize matrix | partial tests only | YELLOW |
| CJK generated document regression | partial document/localization coverage; no explicit Slice 10A trace row | YELLOW |

## P07 — Retry/backoff/attempt accounting

| Requirement | Automated evidence | Status |
|---|---|---|
| retry policy basics | `MessageRetryPolicyTest` | GREEN |
| exact retry exhaustion in worker | `MessageDeliveryWorkerScenarioMatrixTest#retryExhaustionBoundaryIsExact` | GREEN |
| attempt count increments with processing claims | `MessageScenarioMatrixTest#attemptCountMeansActualProcessingClaims` | GREEN |
| retry time must follow claim time | `MessageScenarioMatrixTest#retryMustBeStrictlyAfterProcessingStart` | GREEN |
| due retry dispatcher idempotency | `MessageProcessingIntegrationTest#dispatcherAtomicallyRequeuesDueRetryAndAppendsOneDeliveryEvent` | GREEN |
| concurrent retry dispatchers | `MessageProcessingIntegrationTest#concurrentDispatchersAppendExactlyOneDeliveryEvent` | GREEN |
| Retry-After/backoff cap/deterministic jitter/restart matrix | partial only | YELLOW |

## P08 — Duplicate/idempotency/concurrency/ambiguous outcome

| Requirement | Automated evidence | Status |
|---|---|---|
| second sequential claim is no-op | `MessageProcessingIntegrationTest#claimsOnceAndKeepsTenantAndTerminalStatesIsolated` | GREEN |
| two concurrent claims have one winner | `MessageProcessingIntegrationTest#concurrentBeginHasExactlyOneWinner` | GREEN |
| duplicate delivery event invokes provider once | `MessageProcessingIntegrationTest#duplicateDeliveryEventCausesOneProviderAttempt` | GREEN |
| provider call occurs outside claim transaction | `MessageProcessingIntegrationTest#providerCallRunsAfterClaimTransactionCommits` | GREEN |
| terminal states never return to processing | `MessageScenarioMatrixTest#terminalStatesNeverMoveBackToProcessing` | GREEN |
| 8 workers + 100 duplicate events => one physical provider call, one SENT, one sentCount, attemptCount=1 | new dedicated integration test required | RED |
| recovery vs worker/retry three-way races | no deterministic barrier-based integration matrix located | RED |
| fault points AFTER_CLAIM / BEFORE_PROVIDER / AFTER_PROVIDER_ACCEPTED / BEFORE_STATE_COMMIT / AFTER_STATE_COMMIT | fault injector not implemented | BLOCKED |
| stable physical-delivery idempotency key across retries | no persisted delivery-attempt/idempotency contract located | BLOCKED |
| ACCEPT_THEN_TIMEOUT explicit UNKNOWN/equivalent, no blind resend | production state/ledger contract missing | BLOCKED |
| fixed-seed property/fuzz sequence invariants | no property matrix located | RED |

## P09 — Recovery/stuck/dead/manual operations

| Requirement | Automated evidence | Status |
|---|---|---|
| stale processing recovery and exhausted failure | `MessageProcessingIntegrationTest#recoversOnlyStaleProcessingAndFailsExhaustedAttempt` | GREEN |
| recovery service unit behavior | `MessageRecoveryServiceTest` | GREEN |
| recovery uses centralized state/counter transition | covered through `MessageStateService#recoverStale` tests | GREEN |
| duplicate recovery/concurrent recovery workers | partial/no explicit full race matrix located | YELLOW |
| authorized/unauthorized/cross-tenant manual retry | no traceable HTTP/RBAC retry matrix located | RED |
| cancel while provider in flight / after provider accept | no deterministic test located | RED |
| recovery audit history and batch partial failure | no explicit complete matrix located | RED |

## P10 — API/dashboard/counters/observability

| Requirement | Automated evidence | Status |
|---|---|---|
| tenant-scoped message query/filter baseline | `MessageQueryServiceTest` | GREEN |
| destination masking | `DestinationMaskerTest` | GREEN |
| safe delivery error summary | `DeliveryErrorSummaryTest` | GREEN |
| retry/stuck/dead delivery metrics primitives | `MessageDeliveryMetricsTest` | YELLOW |
| frontend customer/contract/receivable API integration | `CustomerFrontendApiIntegrationTest`, `ContractFrontendApiIntegrationTest`, `ReceivableFrontendApiIntegrationTest` | GREEN |
| message API stable paging/sort/filter combinations | partial query tests | YELLOW |
| API -> DB -> metric consistency | no end-to-end consistency test located | RED |
| bounded metric label cardinality | no explicit cardinality assertion located | RED |
| correlation id and retry correlation continuity | no explicit integration assertion located | RED |
| raw body/attachment/auth header/PII absent from logs | no log-capture abuse suite located | RED |
| alert generation and alert de-duplication | no explicit tests located | RED |

## P11 — Security abuse and authorization

| Requirement | Automated evidence | Status |
|---|---|---|
| anonymous access rejected | `RoleAccessSmokeIntegrationTest#anonymousActorCanOnlyUsePublicSecurityEndpoints` | GREEN |
| tenant-admin allowed zones / platform zone forbidden | `RoleAccessSmokeIntegrationTest#tenantAdminCanUseEveryImplementedTenantManagementZone` | GREEN |
| custom role least privilege | `RoleAccessSmokeIntegrationTest#customRoleReceivesOnlyItsSelectedPermission` | GREEN |
| service client segregated from human/platform APIs | `RoleAccessSmokeIntegrationTest#serviceClientIsRejectedByEveryHumanAndPlatformZone`, `SecurityIntegrationTest#serviceJwtCannotCallHumanPermissionEndpoint` | GREEN |
| wrong JWT audience rejected | `SecurityIntegrationTest#decoderRejectsTokenForAnotherAudience` | GREEN |
| tenant role/permission claims | `SecurityIntegrationTest#tenantAdminTokenContainsExpectedRoleAndPermissions` | GREEN |
| cross-tenant membership mutation hidden | `SecurityIntegrationTest#tenantAdminCanReadOnlyOwnTenantMembers` | GREEN |
| refresh replay revokes token family | `SecurityIntegrationTest#refreshTokenIsRotatedAndReuseRevokesFamily` | GREEN |
| last tenant administrator cannot be demoted/blocked | `SecurityIntegrationTest#lastTenantAdministratorCannotBeDemotedToCustomRole`, `#lastTenantAdministratorCannotBeBlocked` | GREEN |
| OTP single-use | `SecurityIntegrationTest#otpIsSingleUse` | GREEN |
| S01-S45 complete traceable abuse matrix | current tests cover only a subset | RED |
| message/campaign/customer/attachment BOLA/IDOR matrix | partial tenant security, not resource-family exhaustive | RED |
| forged tenantId in body/path/query cannot change scope | no explicit broad matrix located | RED |
| SQL/filter injection | no explicit abuse test located | RED |
| XXE/Billion Laughs/formula injection | no explicit attack suite located | RED |
| CRLF/log injection and sensitive log redaction | no explicit log-capture suite located | RED |
| oversized request/page/filter abuse | no explicit bounded-abuse suite located | RED |
| mock/fault controls absent in prod | no explicit production-context test located | RED |
| SSRF provider endpoint cannot come from user data | no explicit abuse test located | RED |
| stale authorization / disabled user / cross-tenant cache contamination | partial identity lifecycle tests only | YELLOW |

## Execution order

Mandatory red-cell closure order:

1. P08 — duplicate/idempotency/concurrency/ambiguous outcomes.
2. P11 — security abuse and authorization.
3. P02 — import/parser/file abuse.
4. P10 — API/dashboard/observability/log safety.
5. Re-run `./mvnw verify -Pslice10a-coverage` and resolve the JaCoCo gate.
6. Re-run the complete integration suite before considering a PR.

## Merge gate

Do not merge Slice 10A while any mandatory P08/P11/P02/P10 row is `RED` or `BLOCKED`, or while the `slice10a-coverage` workflow is red. `BLOCKED` requires an explicit production-grade design decision, not a test-only workaround.
