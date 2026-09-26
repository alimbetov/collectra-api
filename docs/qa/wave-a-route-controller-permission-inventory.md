# Wave A — Route / Controller / Permission / Test Inventory

Status: AUDITED BASELINE
Base: main@8704e425ca6ea92d277da136cdfe84d07ebf5057

Legend: REAL = usable route/API with matching implementation; PARTIAL = implemented but a user-journey, direct UI-test or authorization gap remains; PLACEHOLDER = routed placeholder; API_ONLY = intentional backend-only surface; VC9 = deferred analytics UI.

| Surface | UI/API | Server authorization | Existing automated evidence | State | Gap |
|---|---|---|---|---|---|
| Platform overview/tenants/users/admins | /platform/* | ROLE_PLATFORM_SUPER_ADMIN | PlatformAuthentication, Overview, TenantLifecycle, UserAdministration, AdministratorManagement + page tests | REAL | retain tenant-principal denial matrix |
| Platform analytics | /platform/analytics + platform analytics API | ROLE_PLATFORM_SUPER_ADMIN | CommunicationAnalyticsIntegrationTest | VC9 | UI deferred |
| Platform audit | /platform/audit | no matching platform audit UI contract established | security/audit backend tests | PLACEHOLDER | product decision |
| Platform operations | /platform/operations | no primary UI contract established | operational tests fragmented | PLACEHOLDER | product decision |
| Dashboard | / + /api/v1/dashboard/* | ROLE_HUMAN | Dashboard page/model + receivable integration | PARTIAL | capability permission not explicit |
| Customers/segments | /customers/* + /api/v1/customers* | ROLE_HUMAN | CustomerFrontendApiIntegrationTest + page/model tests | PARTIAL | tenant predicates evidenced; capability permission not explicit |
| Contracts | /contracts/* + /api/v1/contracts | ROLE_HUMAN | ContractFrontendApiIntegrationTest + page/model tests | PARTIAL | capability permission not explicit |
| Receivables | /receivables/* | ROLE_HUMAN at business zone | ReceivableFrontendApiIntegrationTest + API/filter tests | PARTIAL | authorization granularity review |
| Collections | /collections + /api/v1/collection-cases | ROLE_HUMAN | CollectionHistoryApiIntegrationTest, CollectionQueuePostgresIntegrationTest | PLACEHOLDER | P1 UI gap; no collection capability authority |
| Campaigns | /campaigns/* + /api/v1/campaigns | CAMPAIGN_READ / CAMPAIGN_MANAGE | campaign integration/scenario tests | PARTIAL | direct page tests missing |
| Messages | campaign run message routes | CAMPAIGN_READ | Slice10aP11MessageSecurityIntegrationTest + API/model tests | REAL | preserve masking/hierarchy controls |
| Templates | /templates/* | TEMPLATE_READ/MANAGE/PUBLISH | template integration/unit + selected page tests | PARTIAL | create/detail direct page tests missing |
| Service clients | /integrations/service-clients/* | SERVICE_CLIENT_* | Lifecycle + Security integration | PARTIAL | direct page tests missing |
| Source schemas | /integrations/source-schemas/* | SOURCE_SCHEMA_READ/MANAGE | versioned configuration tests | PARTIAL | direct page tests missing |
| Mapping profiles | /integrations/mapping-profiles/* | MAPPING_PROFILE_READ/MANAGE | mapping/versioned configuration tests | PARTIAL | direct page tests missing |
| Integration sources | /integrations/sources/* | INTEGRATION_SOURCE_READ/MANAGE | IntegrationSourceLifecycleIntegrationTest | PARTIAL | direct page tests missing |
| Technical ingestion | service API only | ROLE_SERVICE + integration scopes | IntegrationPipelineExecutableSmokeTest + ingestion tests | API_ONLY | must stay inaccessible to human-only zones |
| Imports | /imports/* | document/config permissions depending endpoint | import isolation/parser/postgres tests | PARTIAL | direct page tests missing |
| Files | /files/* + /api/v1/files | FILE_READ/UPLOAD/DELETE through FileAuthorization | FileController/FileService integration | PARTIAL | direct page tests missing |
| Tenant communication analytics | backend API | ROLE_HUMAN + CAMPAIGN_READ | CommunicationAnalyticsIntegrationTest | VC9 | UI deferred |
| Auth/session | /login + auth/identity API | public/ROLE_HUMAN | Security, RoleAccess, TenantSecurityManagement | PARTIAL | direct LoginPage test missing |

## Permission-granularity hotspots

Code inspection confirms tenant predicates at the service/repository boundary for Collection, Customer and Contract. That is necessary for horizontal tenant isolation but is not a substitute for vertical authorization.

CollectionController is ROLE_HUMAN-only while Campaign/Message/File/Integration/Template expose capability authorities. Customer, Contract and Dashboard show the same broad ROLE_HUMAN pattern. These are security-review hotspots, not yet declared exploitable defects: Wave B must prove whether a minimally privileged TENANT_USER can invoke business mutations that product policy intended to restrict.

## Existing security evidence

- RoleAccessSmokeIntegrationTest: anonymous denial; tenant-admin zone access; restricted custom role; service principal rejected from human/platform zones.
- TenantSecurityManagementIntegrationTest: permission-aware user options, foreign-tenant isolation, role changes invalidate stale authorization, session revocation.
- ServiceClientSecurityIntegrationTest: cross-tenant service-client mutation hidden as 404; rotated credential invalidates old service JWT.
- Slice10aP11MessageSecurityIntegrationTest: invalid/expired auth, foreign message/run non-disclosure, destination masking, bounded page size.
- ImportTenantIsolationIntegrationTest: foreign mapping execution rejected.
- FileControllerIntegrationTest: tenant-scoped registry, foreign file 404, safe DTO without storage internals.
- CustomerFrontendApiIntegrationTest and ReceivableFrontendApiIntegrationTest: tenant-scoped reads and foreign-resource/business invariant coverage.

Presence of evidence is not a pre-VC9 PASS. All mapped suites must execute on the final hardening SHA.
