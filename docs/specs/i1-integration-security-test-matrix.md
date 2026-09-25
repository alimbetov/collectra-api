# I1 IntegrationSource integration/security test matrix

Status legend: **PASS-COVERED** = executable coverage exists in this branch; **I2+** = intentionally outside I1.

| Area | Scenario | Expected invariant | Executable coverage | Status |
|---|---|---|---|---|
| lifecycle | create source | starts in DRAFT and tenant-owned | `IntegrationSourceLifecycleIntegrationTest.readinessRequiresPublishedCoherentSchemaAndMappingBeforeActivation` | PASS-COVERED |
| readiness | unpublished schema/mapping | readiness is blocked; activation rejected | same lifecycle test | PASS-COVERED |
| readiness | published coherent schema/mapping | all mandatory checks READY | same lifecycle test | PASS-COVERED |
| coherence | mapping points to another schema version | `MAPPING_SCHEMA_COHERENT=BLOCKED`; activation rejected | `rejectsActivationWhenPublishedMappingTargetsAnotherSchemaVersion` | PASS-COVERED |
| lifecycle | activate/suspend/reactivate/archive | legal state transitions persist and increment version | lifecycle test | PASS-COVERED |
| optimistic concurrency | stale version | stable `VERSION_CONFLICT` | `rejectsCrossTenantReferencesDuplicateCodesAndStaleVersions` | PASS-COVERED |
| uniqueness | duplicate tenant source code | stable `INTEGRATION_SOURCE_CODE_EXISTS` | same test | PASS-COVERED |
| tenant isolation | foreign ServiceClient reference | create rejected | same test | PASS-COVERED |
| tenant isolation | foreign source read | resource is not disclosed | same test | PASS-COVERED |
| D3 credentials | create secret | server-generated; returned once; safe DTO does not persist/expose secret | `ServiceClientLifecycleIntegrationTest` | PASS-COVERED |
| D3 rotation | overlap window | old and new credentials authenticate before activation | `ServiceClientLifecycleIntegrationTest` | PASS-COVERED |
| D3 rotation | activation | old secret and previously issued JWT invalidated via authorizationVersion | `ServiceClientSecurityIntegrationTest` | PASS-COVERED |
| D3 tenant isolation | rotate/block foreign client | 404/no disclosure | `ServiceClientSecurityIntegrationTest` | PASS-COVERED |
| platform lifecycle | blocked tenant | service credential access is revoked using server-issued secret | `PlatformTenantLifecycleIntegrationTest` | PASS-COVERED |
| RBAC contract | source permissions | `INTEGRATION_SOURCE_READ`, `INTEGRATION_SOURCE_MANAGE`, `INGESTION_READ` seeded for TENANT_ADMIN | Liquibase 045 + controller `@PreAuthorize` | PASS-COVERED |
| machine/human separation | service token on source control-plane | source CRUD remains HUMAN-only; machine ingestion belongs to I2 | controller class/method guards | PASS-COVERED |
| ingestion | source-oriented 202/idempotency/raw archive | durable async ingestion contract | I2 | I2+ |
| ingestion security | required headers/protected headers | validate before queue | I2 | I2+ |
| remote resources | SSRF/redirect/rebinding/limits | hardened URL import | I4 | I2+ |
| recovery | worker lease/stale recovery/backpressure | duplicate-safe processing | I5 | I2+ |

## I1 exit criteria

I1 is complete only when the branch compiles, the full Maven verification and frontend pipeline are green on the exact merge candidate SHA, and the lifecycle/D3 tests above execute in that run. I2+ rows are not blockers for I1 and must not be represented as implemented.
