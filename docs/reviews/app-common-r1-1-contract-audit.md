# APP-COMMON-R1.1 — Contract consistency review and RED test readiness
Date: 2026-11-14
Status: AUTHOR ARCHITECTURAL REVIEW / INDEPENDENT S2 NOT YET PERFORMED
Branch: feat/iam-shared-platform
Reviewed: APP-COMMON-R1 spec, US-01..US-24 matrix, ADR-IAM-007, ADR-IAM-008.

## Verdict
**CONDITIONAL DESIGN BASELINE; IMPLEMENTATION HOLD.** The 24/24 mapping is documentary coverage, not API existence or executed tests. This review was produced by the authoring assistant and **must not be presented as independent S2 approval**.

## Findings
| ID | Severity | Finding | Required correction / proof |
|---|---|---|---|
| R11-P0-01 | P0 | One shared end-user realm and separate operator realm are described, but 'one realm' shorthand is ambiguous | Explicitly distinguish **one end-user realm** vs isolated operator realm in deployment and tests |
| R11-P0-02 | P0 | No actual App Common repository/runtime/CI evidence; proposed endpoints in traceability matrix can be mistaken for implemented | Pin repository, create implementation skeleton, publish OpenAPI contract and RED CI evidence before GREEN |
| R11-P0-03 | P0 | Global user disablement and previously issued JWT across all apps lack approved enforcement protocol/SLA | Define policy version distribution, authenticated acknowledgements, expiry and fail-closed tests |
| R11-P0-04 | P0 | Collectra tenant authorization after shared SSO requires verified local membership; first-login path risks automatic tenant creation | Require explicit invitation/onboarding policy, unique local mapping, cross-tenant denial |
| R11-P1-01 | P1 | R1 matrix references unqualified paths and proposed consumer endpoints without explicit base URL or owner | Publish provider-owned OpenAPI and consumer contract files separately; mark proposals |
| R11-P1-02 | P1 | Test IDs encode scenarios but not executable code, assertions, fixtures, CI or evidence | Add test manifest, real Keycloak/PostgreSQL/SMTP harness and RED output |
| R11-P1-03 | P1 | Client Credentials and user-token scopes/audiences need explicit grant/claim mapping | Define realm client scopes, audience mappers, resource-server allowlists, negative tests |
| R11-P1-04 | P1 | Account deletion in one app can accidentally delete shared global user | Separate app profile deletion from global account closure and two-stage authorization |
| R11-P1-05 | P1 | Template lifecycle and provider callbacks need schema/authorization/replay contract | Immutable version, callback signature, event ordering and unknown-delivery reconciliation |
| R11-P1-06 | P1 | BFF/SPA, cookie/CSRF and refresh rotation unresolved | Choose topology, threat model and browser E2E tests |
| R11-P1-07 | P1 | Migration of local JWTs and verified account links lacks executable rollback | Build PostgreSQL fixtures preserving UUID, FKs and tenancy, simulate interrupted cutover |
| R11-P2-01 | P2 | Main spec and matrix use different levels of API path specificity | Normalize path ownership and prefix in OpenAPI before implementation |

## Proof sequence
1. **S2 contract review:** independent reviewer examines this register, ADRs and traceability, records approval or rejection with evidence.
2. **Contract RED:** publish OpenAPI for App Common internal API and Collectra adapter contract; run contract tests that fail if endpoint/security/status contracts are missing.
3. **Infrastructure RED:** use real Keycloak shared end-user realm, two clients with distinct audiences, PostgreSQL schema/transactions and deterministic SMTP fixture. Verify failures are due to missing behavior, not broken test infrastructure.
4. **Minimal GREEN:** implement only required P0 contracts, rerun tests and preserve logs.
5. **Browser E2E:** prove registration once, SSO into second app without Collectra tenant, and Collectra tenant denial.
6. **S2 signoff:** independent approval plus restore/security regression before release.

## RED test inventory (first tranche)
| Test ID | Journey | Required setup | RED expectation |
|---|---|---|---|
| R11-RED-01 | US-01 | shared realm + App Common API + PostgreSQL | app provisioning creates client, not realm; idempotent |
| R11-RED-02 | US-02 | Keycloak verification + Collectra PostgreSQL | unverified denied; concurrent onboarding yields one local UUID |
| R11-RED-03 | US-03 | two API audiences + Keycloak | wrong-audience bearer rejected |
| R11-RED-04 | US-13 | same realm, Collectra + App-B | same sub, no second registration, App-B no tenant |
| R11-RED-05 | US-20 | Collectra membership fixture | revoked local membership denied with previously issued JWT |
| R11-RED-06 | US-09 | PostgreSQL + SMTP fault fixture | idempotency and DELIVERY_UNKNOWN after ambiguous acceptance |
| R11-RED-07 | US-11 | app suspension + resource server | existing JWT rejected after policy enforcement ack |
| R11-RED-08 | US-14 | legacy Collectra FK fixtures | local UUID and FKs unchanged; email-only link rejected |

## Acceptance of this stage
A review document and executable RED test scaffolding are useful but **not sufficient** for R1.1 closure. Closure requires independent S2 review and a recorded, reproducible failing test run on real dependencies. Never call a failing test RED if it only fails because the test environment or contract fixture is missing.

## Current status
Architecture reviewed by authoring assistant; independent S2 not performed. Test manifest planned; 0 infrastructure RED runs evidenced; 0 GREEN runs evidenced.