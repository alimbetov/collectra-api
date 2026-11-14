# R1.6C security and process consistency audit — 2026-11-14
Status: INDEPENDENT CODE INSPECTION / NOT AN INDEPENDENT S2 APPROVAL / RELEASE HOLD

Scope: feat/iam-shared-platform, Collectra SecurityConfig, KeycloakDomainAuthorizationFilter, AuthorizationVersionFilter, GlobalRevocationGate, RevocationSnapshotStore/Consumer, App Common SubjectAccessService, RevocationDispatcher, EnforcementReconciler, RevocationConsumerController, KeycloakAdminClient, YAML and SQL migrations. No Maven, PostgreSQL, Docker, or HTTP test execution in this audit.

## Confirmed defects
- P0-AUTH-01: AuthorizationVersionFilter.validUserToken uses jwt.getToken().getSubject() as UUID. Domain adapter intentionally preserves Keycloak global subject and sets authentication name to local UUID. Tenant user JWT will be denied by legacy filter. Fix by deriving validated local user identity from authentication name *only for domain-adapted tokens*, preserving legacy/service semantics. Add HTTP tests.
- P0-CONFIG-02: app-common/src/main/resources/application.yml places keycloak and revocation below management instead of app-common. @Value(app-common.keycloak.*) and @Value(app-common.revocation.*) read defaults, ignoring YAML. Correct indentation and configuration binding; assert enabled/disabled flags in startup tests.
- P0-REV-03: SubjectAccessService.block increments version for every replay; subject_block_idempotency table exists but is unused. Concurrent same-key replay must return original operation, not increment version.
- P0-ENF-04: EnforcementReconciler only requires ACKs from *currently enabled registered* consumers. One registered consumer can yield ENFORCED even if mandatory App-B absent. Freeze required consumer set per operation, or gate against expected registry topology and require both named consumers.
- P0-ENF-05: App Common snapshot endpoint returns ACTIVE v0 for unknown subject, while Collectra consumer rejects version <1. No safe initial ACTIVE onboarding flow; first user cannot pass fresh snapshot gate.
- P0-E2E-06: No independent App-B server, no four-service compose, no real user-token HTTP E2E or fault-injection evidence. Release criterion unproven.
- P0-SEC-07: Keycloak resource-server mode and legacy local HS256 JWT mode are mutually exclusive; legacy sessions not covered by global revocation. Rollout/rollback must explicitly revoke legacy sessions or enforce dual-mode policy.
- P1-SCALE-08: RevocationSnapshotConsumer selects ORDER BY subject_id LIMIT 10000, without pagination, refreshes one token per subject, and silently swallows errors. No bounded propagation guarantee, alerting or fair progress.
- P1-REV-09: RevocationSnapshotStore permits newer ACTIVE after BLOCKED; unblock policy and authorization missing. Must define and verify transition semantics before enabling.
- P1-DELIVERY-10: RevocationDispatcher sets published_at after Keycloak disable/logout, not after resource-server delivery; outbox published is semantically misleading. Keycloak action and consumer delivery should be independently tracked.
- P1-SEC-11: ACK is bound to OAuth azp but not to evidence of durable consumer enforcement; version <= current check is insufficient for end-to-end trust. Consumer must ACK after committed local snapshot and tests must prove it.
- P1-OPS-12: KeycloakAdminClient requires issuer string equality with base URL; external issuer and internal admin URL can differ in containers. Separate canonical issuer from admin transport base; validate realm identity safely.
- P1-FILTER-13: KeycloakDomainAuthorizationFilter is a @Component OncePerRequestFilter and also added explicitly to SecurityFilterChain. Verify/disable servlet auto-registration to avoid double invocation and incorrect ordering.
- P1-TEST-14: RevocationSnapshotPostgresIT creates isolated table directly; does not test Liquibase migrations 054/055. CI workflow added but successful run not evidenced.
- P1-TEST-15: KeycloakModeSecurityTest uses ReflectionTestUtils on private converter; does not exercise HTTP filter chain, tenant isolation, Keycloak signature validation or actual denial.
- P1-SEC-16: App Common ResourceServerSecurity leaves default CSRF protection enabled for POST bearer API. Validate and explicitly configure stateless bearer-only chain (not BFF), otherwise block/ACK may return 403.
- P1-RELIABILITY-17: Dispatcher claims rows by postponing next_attempt_at, not a renewable lease; if Keycloak call outlasts lease, duplicate concurrent admin operations can occur. Reconciliation and idempotent admin calls required.

## Process contract / expected state machine
ACTIVE vN -> global BLOCKED vN+1 + durable operation/outbox (single transaction) -> Keycloak disable/logout -> local Collectra BLOCKED vN+1 durable commit -> Collectra ACK -> independent App-B BLOCKED vN+1 durable commit -> App-B ACK -> ENFORCED. Failures must remain pending/retryable; missing ACK must not be ENFORCED. Older ACTIVE events cannot reverse BLOCKED. Unknown/stale local snapshot must deny.

## Recommended correction sequence
1. P0-AUTH-01 and P0-CONFIG-02 RED tests, minimal fixes, verify build.
2. Correct CSRF, filter registration, identity link onboarding and initial ACTIVE version.
3. Implement block idempotency, mandatory consumer topology and independently tracked Keycloak/consumer states.
4. Consumer pagination, token reuse, metrics, durable reconciliation, policy-driven unblock.
5. PostgreSQL Liquibase Testcontainers and Spring Security HTTP tests.
6. Independent App-B, Keycloak and four-service HTTP E2E with fault injection.
7. Independent S2 review with CI logs and production gates. No merge into main.

No claims of executed tests or independent approval are made.
