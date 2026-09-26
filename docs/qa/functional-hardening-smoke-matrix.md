# Functional Hardening — Smoke Matrix

Status: WAVE-A INVENTORIED
Result vocabulary: NOT_RUN | PASS | FAIL | BLOCKED | WAIVED(reason)
Implementation state: REAL | PARTIAL | PLACEHOLDER | API_ONLY | VC9

| ID | Persona | Surface | State | Actual automated evidence mapped | Isolation/security variant | Result |
|---|---|---|---|---|---|---|
| SM-001 | Platform Super Admin | platform tenants/users/admins | REAL | PlatformAuthenticationIntegrationTest; PlatformOverviewIntegrationTest; PlatformTenantLifecycleIntegrationTest; PlatformUserAdministrationIntegrationTest; PlatformAdministratorManagementIntegrationTest; platform page tests | tenant token -> platform denied; no implicit tenant-business context | NOT_RUN |
| SM-010 | Tenant Admin | identity/roles/sessions | REAL | SecurityIntegrationTest; RoleAccessSmokeIntegrationTest; TenantSecurityManagementIntegrationTest; RbacSeedIntegrationTest | foreign tenant membership; stale permission/session; last-admin invariants | NOT_RUN |
| SM-020 | Integration Manager | service clients | PARTIAL | ServiceClientLifecycleIntegrationTest; ServiceClientSecurityIntegrationTest | foreign rotate/block -> non-disclosure; old JWT after rotation | NOT_RUN |
| SM-021 | Integration Manager | schema/mapping/source | PARTIAL | VersionedConfigurationIntegrationTest; MappingMetadataIntegrationTest; IntegrationSourceLifecycleIntegrationTest | foreign schema/mapping/source references; permission denial | NOT_RUN |
| SM-030 | Technical Client | production ingestion | API_ONLY | IntegrationPipelineExecutableSmokeTest; production ingestion/import tests | wrong scope; blocked client; human/service confusion; foreign source | NOT_RUN |
| SM-040 | Operator | customers/segments | PARTIAL | CustomerFrontendApiIntegrationTest; customer page/model tests | foreign customer/segment/manager; minimal-role direct mutation | NOT_RUN |
| SM-041 | Operator | contracts | PARTIAL | ContractFrontendApiIntegrationTest; contract page/model tests | foreign contract/customer; minimal-role direct mutation | NOT_RUN |
| SM-042 | Operator | receivables/allocation | PARTIAL | ReceivableFrontendApiIntegrationTest; CustomerReceivableCoreIntegrationTest | foreign invoice/payment; idempotency; minimal-role mutation | NOT_RUN |
| SM-050 | Collection Officer | collection workspace | PLACEHOLDER | CollectionHistoryApiIntegrationTest; CollectionQueuePostgresIntegrationTest | foreign case/child IDs; minimal-role direct mutation | BLOCKED |
| SM-060 | Content Manager | templates | PARTIAL | TemplateBuilderContractIntegrationTest; TemplateStudioContractIntegrationTest; template unit/page tests | foreign asset/version/template; publish/manage separation | NOT_RUN |
| SM-070 | Campaign Manager | campaigns/runs | PARTIAL | CampaignContractClosureIntegrationTest; CampaignStabilizationIntegrationTest; MultichannelCampaignCoreIntegrationTest | foreign campaign/run/customer/template; READ vs MANAGE | NOT_RUN |
| SM-071 | Campaign/Support | message monitoring | REAL | Slice10aP11MessageSecurityIntegrationTest; MessageProcessingIntegrationTest; message frontend API/model tests | foreign message/run; raw destination absent; bounded page | NOT_RUN |
| SM-080 | File operator | files | PARTIAL | FileControllerIntegrationTest; FileServiceApiIntegrationTest | foreign file 404; no storage internals; FILE_* separation | NOT_RUN |
| SM-090 | Import operator | imports | PARTIAL | ImportTenantIsolationIntegrationTest; ImportDiagnosticPostgresIntegrationTest; ImportOperationsPostgresIntegrationTest; parser security tests | foreign mapping; parser/file abuse; bounded input | NOT_RUN |
| SM-100 | System | multichannel mock delivery | API_ONLY | MultichannelMockDeliverySmokeIntegrationTest; MessageDeliveryWorkerScenarioMatrixTest | tenant-scoped message/run; duplicate/retry/attachment gates | NOT_RUN |
| SM-110 | System/Support | recovery/concurrency | API_ONLY | Slice10aP08ConcurrencyIntegrationTest; MessageRecoveryServiceTest; outbox/claim tests | stale/duplicate event must not cross tenant or double count | NOT_RUN |
| SM-120 | Security | global BOLA/IDOR matrix | PARTIAL | SecurityIntegrationTest; RoleAccessSmokeIntegrationTest + domain-specific isolation tests | paired Alpha/Beta IDs for every tenant-owned resource | NOT_RUN |
| SM-121 | Security | vertical authorization | PARTIAL | RoleAccessSmokeIntegrationTest proves selected identity zones | restricted persona directly calls Customer/Contract/Receivable/Collection mutations | NOT_RUN |
| SM-122 | Security | token/credential revocation | REAL | TenantSecurityManagementIntegrationTest; ServiceClientSecurityIntegrationTest | stale user token/session and old service secret/JWT | NOT_RUN |
| SM-123 | Security | DTO/log/PII leakage | PARTIAL | Slice10aP11MessageSecurityIntegrationTest; FileControllerIntegrationTest | raw destination/provider body/secrets/storage internals absent | NOT_RUN |
| SM-130 | Build | full pre-VC9 gate | PARTIAL | OpenApiCompatibilityIntegrationTest + project CI | clean DB, PostgreSQL/RabbitMQ, frontend, OpenAPI, all smoke suites | NOT_RUN |
| SM-140 | Platform | analytics UI | VC9 | CommunicationAnalyticsIntegrationTest backend only | tenant/platform analytics isolation retained | NOT_RUN |
| SM-141 | Platform | audit UI | PLACEHOLDER | tenant security-audit API evidence only | product decision required | BLOCKED |
| SM-142 | Platform | operations UI | PLACEHOLDER | fragmented operational backend tests | product decision required | BLOCKED |

## Channel boundary expansion

For each implemented channel: SUCCESS; PERMANENT_FAILURE; TRANSIENT_FAILURE_THEN_SUCCESS; RETRY_EXHAUSTED; TIMEOUT_BEFORE_ACCEPT; ambiguous accept/timeout where modeled; INVALID_DESTINATION; REQUIRED_ATTACHMENT_PENDING; REQUIRED_ATTACHMENT_FAILED; DUPLICATE_QUEUE_EVENT.

A mapped test is evidence of coverage design, not PASS. PASS requires execution on the recorded verification SHA.
