# Slice 10A — Test Users, Roles and Authorization Matrix

Status: NORMATIVE TEST FIXTURE
Parent specs:
- `docs/specs/slice-10a-multichannel-mock-smoke-lab.md`
- `docs/specs/slice-10a-user-security-scenario-matrix.md`

## 1. Goal

Define deterministic test identities for Slice 10A so every user journey, API test, retry/recovery operation, tenant-isolation check and security-abuse scenario runs under an explicit principal with explicit permissions.

The test lab must never rely on one omnipotent `admin` principal for all scenarios.

Each protected action must have at least:

1. one positive authorization case;
2. one insufficient-role case;
3. one foreign-tenant case;
4. one unauthenticated/invalid-auth case where applicable.

The implementation must map these fixture personas onto the actual Collectra authorities/roles that exist in runtime code. If production authorities use different names, keep the persona semantics and adapt only the mapping.

## 2. Test tenants

Create at least three isolated tenants:

```text
TENANT_ALPHA   normal active tenant
TENANT_BETA    second active tenant used for cross-tenant isolation tests
TENANT_GAMMA   disabled/suspended tenant used for lifecycle/security tests
```

Every tenant must have separate:

- users;
- technical clients;
- customers;
- campaigns;
- messages;
- templates;
- attachments;
- provider/channel configuration;
- idempotency keys;
- audit records.

IDs should intentionally be easy to reference in fixtures but must never make tenant isolation depend on ID ranges or prefixes.

## 3. Human test personas

For both `TENANT_ALPHA` and `TENANT_BETA`, create the following principals.

| Fixture principal | Persona | Intended access |
|---|---|---|
| `tenantAdmin` | Tenant Administrator | Tenant configuration, users/roles where supported, campaigns, messages, templates, manual operational actions |
| `campaignManager` | Campaign Manager | Campaign create/start/pause/resume/cancel, campaign/message views, no tenant/security configuration |
| `collectionOperator` | Collection / Customer Care Operator | Customer/message operational views and allowed business actions, no provider/config administration |
| `supportOperator` | Support / Ops | Operational diagnostics, masked delivery data, retry/recovery operations only if explicitly authorized |
| `auditor` | Read-only Auditor | Read-only tenant-scoped reporting/audit, no mutation |
| `templateEditor` | Template Manager | Template CRUD/preview where supported, no campaign execution/provider configuration |
| `attachmentOperator` | Document/Attachment Operator | Attachment/document operations where supported, no campaign/provider administration |
| `readOnlyUser` | Minimal authenticated reader | Explicit read-only subset; mutation forbidden |
| `disabledUser` | Disabled account | Authentication/session must fail according to current auth model |
| `revokedUser` | Previously valid user whose access is revoked | Used for stale-token / re-authorization tests |

Do not automatically grant every persona access to all read APIs. The runtime authorization contract remains least-privilege.

## 4. Platform-level personas

If Collectra has or later introduces platform-level administration, keep it separate from tenant administration.

```text
platformSupport
platformAdmin
```

Requirements:

- platform role must be explicit;
- tenant administrator must never gain platform scope;
- platform support must not automatically receive raw PII;
- platform operations must be auditable;
- platform-level access must be tested separately from ordinary tenant flows.

If platform roles are not currently implemented, these fixtures remain future-facing and must not be simulated by bypassing authorization.

## 5. Technical clients

Create deterministic machine principals for integration/API tests.

| Client | Purpose |
|---|---|
| `importClient` | Allowed business-data import only |
| `campaignApiClient` | Allowed campaign/API operations explicitly granted to technical clients |
| `readApiClient` | Read-only technical integration |
| `disabledApiClient` | Disabled/revoked credential behaviour |
| `wrongScopeClient` | Valid credential with insufficient authorities |
| `betaImportClient` | Same as importClient but owned by TENANT_BETA for cross-tenant tests |

A technical client must be tenant-scoped just like a human principal.

Technical-client authentication must never infer tenant scope from request body/query parameters.

## 6. Untrusted principals

The security suite must also use synthetic hostile identities:

```text
anonymousClient
invalidTokenClient
expiredTokenClient
forgedTenantTokenClient
malformedAuthHeaderClient
replayedCredentialClient
```

These fixtures must not contain production-like secrets.

## 7. Authentication fixture model

Recommended test abstraction:

```java
public record TestPrincipal(
        String username,
        UUID tenantId,
        Set<String> authorities,
        PrincipalType type,
        PrincipalStatus status
) {}
```

Recommended fixture factory:

```java
SecurityTestPrincipalFactory.alphaTenantAdmin();
SecurityTestPrincipalFactory.alphaCampaignManager();
SecurityTestPrincipalFactory.alphaSupportOperator();
SecurityTestPrincipalFactory.alphaAuditor();
SecurityTestPrincipalFactory.betaTenantAdmin();
SecurityTestPrincipalFactory.betaCampaignManager();
SecurityTestPrincipalFactory.alphaImportClient();
SecurityTestPrincipalFactory.anonymous();
SecurityTestPrincipalFactory.expiredToken();
```

Tests should request a principal by persona, not hard-code JWT claims throughout the suite.

## 8. Tenant context invariant

For authenticated requests:

```text
JWT/API credential tenant
        ↓
SecurityContext
        ↓
application tenant context
        ↓
service/repository scoped lookup
```

Client-controlled values must not redefine that scope:

```text
body.tenantId
query.tenantId
path tenant id
imported tenant field
custom field
```

Any mismatch is rejected or ignored according to the endpoint contract.

## 9. Authorization matrix

The exact authority names must follow current runtime code, but the semantic matrix is normative.

Legend:

```text
A = allowed
D = denied
M = masked/read-restricted
N = not applicable
```

| Operation | Tenant Admin | Campaign Manager | Collection Operator | Support/Ops | Auditor | Template Editor | Read-only | Integration Client |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| View own-tenant campaign | A | A | A/M | A/M | A | M | A/M | scope-dependent |
| Create campaign | A | A | D | D | D | D | D | scope-dependent |
| Start/pause/resume campaign | A | A | D | D | D | D | D | scope-dependent |
| Cancel campaign | A | A | D | scope-dependent | D | D | D | scope-dependent |
| View message list | A | A | A | A/M | A/M | M | A/M | scope-dependent |
| View raw destination | policy-dependent | policy-dependent | policy-dependent | normally M | M | D | M | D |
| Manual retry | A or explicit authority | explicit authority only | D | explicit authority only | D | D | D | D |
| Recovery/stuck operation | explicit authority | D | D | explicit authority | D | D | D | D |
| Provider/channel config read | A | M/D | D | M | M/D | D | D | D |
| Provider/channel config mutate | A or dedicated admin authority | D | D | D | D | D | D | D |
| Template create/update | A | policy-dependent | D | D | D | A | D | D |
| Attachment/document read | A | A | A/M | A/M | A/M | policy-dependent | A/M | D |
| Attachment/document mutate | A | policy-dependent | policy-dependent | D | D | policy-dependent | D | D |
| Import business data | A | policy-dependent | policy-dependent | D | D | D | D | importClient only |
| Audit/event read | A | M | M | A/M | A | D | M | D |
| User/role administration | A | D | D | D | D | D | D | D |

Any `policy-dependent` cell must become explicit before implementation is considered complete. It must not remain implicit in controller behaviour.

## 10. Cross-tenant matrix

For every protected resource family, execute the same request using:

```text
owner principal + owner resource       -> permitted according to role
same-tenant insufficient role          -> 403/no mutation
foreign-tenant same role               -> 404/403 non-disclosing policy
anonymous                              -> 401
invalid/expired token                  -> 401
```

Resource families:

- customer;
- contract;
- invoice/payment where exposed;
- campaign;
- campaign run;
- message;
- message attachment;
- generated document;
- template;
- provider/channel configuration;
- retry/recovery command;
- audit/event;
- import job.

## 11. Required role-centric tests

At minimum, add parameterized tests equivalent to:

```text
tenantAdminCanOperateOwnTenant
campaignManagerCannotChangeProviderConfig
collectionOperatorCannotStartCampaign
supportSeesMaskedDestination
auditorCannotMutateCampaign
templateEditorCannotRetryDelivery
readOnlyUserCannotMutateAnything
integrationClientCannotUseHumanAdminEndpoints
disabledUserCannotAuthenticate
revokedUserCannotExecuteSecuritySensitiveCommand
alphaAdminCannotReadBetaMessage
alphaCampaignManagerCannotCancelBetaCampaign
alphaSupportCannotRetryBetaMessage
betaImportClientCannotImportIntoAlphaTenant
anonymousCannotDiscoverResourceExistence
expiredTokenCannotExecuteCommand
forgedBodyTenantIdCannotChangeScope
```

## 12. Role-combination and privilege-escalation tests

Add negative tests for role manipulation:

1. user cannot grant themselves a stronger role;
2. campaign manager cannot create tenant admin;
3. support operator cannot promote another user;
4. technical client cannot supply arbitrary authorities in request payload;
5. JWT role/authority data is accepted only after trusted token verification;
6. unknown authority is not treated as admin;
7. empty authority list does not inherit defaults accidentally;
8. duplicate/combined roles produce union only of legitimately granted permissions;
9. removal of a role takes effect according to the documented revocation/session contract;
10. cache keys for authorities include principal + tenant + relevant version/revision so role changes cannot leak stale permissions indefinitely.

## 13. User state tests

Each applicable role should also be exercised under account state changes:

```text
ACTIVE
DISABLED
LOCKED (if supported)
DELETED/DEACTIVATED (if supported)
ROLE_REVOKED
TENANT_SUSPENDED
CREDENTIAL_EXPIRED (if supported)
```

Do not invent runtime statuses that do not exist. Map this list to actual application states during implementation.

## 14. Data visibility tests

Authorization is not only endpoint access. Verify field-level exposure.

Examples:

- support sees masked email/phone where policy requires;
- auditor does not receive provider credentials;
- technical client never receives UI-only/internal security fields;
- provider error normalization hides raw provider secrets/internal responses;
- message body/attachment content is not returned from summary APIs unless explicitly authorized;
- foreign-tenant nested relations are never serialized accidentally;
- API error payload does not reveal another tenant's customer/message existence.

## 15. Audit requirements

Security-sensitive successful and denied commands should be auditable according to existing audit design.

Minimum fields where available:

```text
actorId
actorType
actorTenantId
action
resourceType
resourceId or safe internal reference
result ALLOWED/DENIED
reasonCode
occurredAt
correlationId
```

Never store raw passwords, tokens, Authorization headers, provider secrets or message/attachment bodies in audit records.

## 16. Deterministic test data

Use synthetic identities only, for example:

```text
admin.alpha@test.invalid
campaign.alpha@test.invalid
operator.alpha@test.invalid
support.alpha@test.invalid
auditor.alpha@test.invalid
admin.beta@test.invalid
```

Use `.invalid` domains and synthetic phone numbers reserved for tests/fixtures. No real customer/user data is permitted.

Passwords/API keys in tests must be obvious non-production fixtures and loaded only in test profile.

## 17. Integration with the 30-variation rule

Role/persona is an explicit test dimension, not a separate afterthought.

For each critical process P01-P10, scenario generation should combine business state with relevant actor classes without creating an uncontrolled Cartesian explosion.

Recommended pattern:

```text
30+ business/failure variations
  + positive authorized actor
  + representative insufficient-role actor
  + foreign-tenant actor for tenant-owned resources
  + anonymous/invalid-auth at API boundary
```

Use risk-based pairwise coverage for less critical role combinations and exhaustive checks for security-sensitive commands.

## 18. Recommended test utilities

```text
SecurityTestPrincipalFactory
TenantFixtureFactory
AuthorizationScenarioFactory
JwtTestTokenFactory / ApiCredentialTestFactory
SecurityRequestExecutor
FieldVisibilityAssertions
AuditAssertions
```

Prefer helpers such as:

```java
assertAllowed(principal, request);
assertForbidden(principal, request);
assertForeignTenantHidden(principal, request);
assertMasked(response, "destination");
assertNoSensitiveData(responseOrLogs);
```

These helpers must still execute the real Spring Security/filter/controller/service path for integration tests. They must not bypass authorization by invoking services directly when the test is intended to validate API security.

## 19. Merge gates

Slice 10A is not merge-ready unless:

1. deterministic users exist for at least two active tenants;
2. human and technical-client principals are separate;
3. all protected resource families have positive, insufficient-role, foreign-tenant and unauthenticated coverage;
4. security-sensitive commands re-authorize on execution;
5. disabled/revoked principal cases are covered according to actual auth model;
6. field-level masking/visibility is tested by role;
7. tenant IDs supplied by clients cannot override authenticated tenant scope;
8. mock/fault-injection controls cannot be accessed through normal production principals;
9. role changes do not create stale cross-tenant/privilege cache behaviour;
10. authorization tests execute locally under the `security` profile and are deterministic.
