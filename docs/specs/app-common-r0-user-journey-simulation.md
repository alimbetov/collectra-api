# APP-COMMON-R0.3 — User Journey Simulation & Functional Coverage
Status: DESIGN SIMULATION / NOT EXECUTED
Date: 2026-11-14
Branch: feat/iam-shared-platform
Dependencies: ADR-IAM-004, ADR-IAM-005, APP-COMMON-R0 sections 1–13

## Purpose and proof levels
Simulate actor-driven interactions and verify functional completeness before implementation. Distinguish **DESIGNED** (specified behavior), **SIMULATED** (reasoned trace), **AUTOMATED** (executable test exists), **GREEN** (test actually passed), **E2E VERIFIED** (browser/provider/infrastructure evidence). All journeys here are DESIGN SIMULATED ONLY; no implementation or test execution is claimed.

## Actors and ownership
Platform operator (App Common operator realm), Collectra end user (Collectra realm), Collectra business admin (Collectra local RBAC), Collectra backend (machine identity), App Common API, Keycloak, App Common Notification Worker, SMTP provider, PostgreSQL, independent test application B. App Common cannot mutate Collectra local roles or memberships.

## Journey catalog
| ID | Journey | Happy path | Essential alternate/negative paths | Owner | Test gate |
|---|---|---|---|---|---|
| US-01 | Register application | operator registers -> provisions realm/clients -> validates -> activates | duplicate key, realm collision, partial provisioning, unauthorized operator | App Common | PostgreSQL + Keycloak |
| US-02 | New user registration | OIDC signup -> email verified -> Collectra onboarding -> active local profile | unverified email, duplicate callback, slug collision, aborted provisioning | Keycloak + Collectra | Keycloak + PostgreSQL + browser |
| US-03 | Login and API | PKCE login -> JWT -> Collectra validates issuer/audience -> local membership | wrong audience, expired JWT, ID token, blocked local user | Keycloak + Collectra | HTTP + browser |
| US-04 | Session lifecycle | refresh -> API -> logout -> new login | stolen refresh, concurrent refresh, logout with unexpired JWT | Keycloak + Collectra | browser + API |
| US-05 | Password recovery | Keycloak reset -> verify ownership -> new login | expired/replayed link, email enumeration, outage | Keycloak | browser + SMTP |
| US-06 | Email change | verify new email -> update authoritative identity -> project into Collectra | pending change, stale event, wrong ownership, replay | Keycloak + Collectra | Keycloak + PostgreSQL |
| US-07 | Admin role and MFA | Collectra role promotion -> step-up -> privileged action | stale assurance, demotion, forged claims, MFA reset | Collectra + Keycloak | security HTTP |
| US-08 | Operator MFA | platform operator authenticates -> fresh MFA -> approved app action | Collectra admin tries operator API, self-approval | App Common | Keycloak + HTTP |
| US-09 | Send email | Collectra client credentials -> app scope -> recipient resolve -> outbox -> provider accepted | duplicate, recipient changed, SMTP ambiguous, worker crash | App Common + Collectra | PostgreSQL + SMTP |
| US-10 | Notification status | request status -> provider callback -> delivered only on evidence | duplicate/forged/out-of-order callback | App Common | webhook HTTP |
| US-11 | Suspend application | operator command -> durable operation -> Keycloak login stop + notification stop + resource policy ack | stale JWT, partial ack, cache expiry, control-plane outage | App Common + Collectra | Keycloak + HTTP + fault injection |
| US-12 | Reactivate application | operator approval -> config reconciliation -> enforcement policy update -> active | policy drift, failed realm readiness, stale config | App Common | Keycloak + HTTP |
| US-13 | Second app isolation | provision App-B -> authenticate same email in both realms -> separate local identities | cross-realm JWT, cross-app notification, same email linking | App Common + apps | two-realms E2E |
| US-14 | Legacy Collectra migration | prove account ownership -> link issuer/subject -> atomic cutover -> preserve UUID/roles | timeout after identity creation, concurrent reset, old JWT still valid | Collectra | PostgreSQL + Keycloak |
| US-15 | Keycloak outage | existing valid JWT accepted under fresh local policy -> recover login | new login fails, JWKS unknown kid, stale policy fail-closed | all | fault injection |
| US-16 | Backup and restore | restore App Common DB and Keycloak -> reconcile -> resume jobs | duplicate outbox, mismatched keys/config, RPO/RTO exceeded | operations | restore drill |
| US-17 | Decommission | two-person approval -> disable auth/notifications -> retention/export -> terminal state | active sessions, pending email, other realm unaffected | App Common | E2E + audit |
| US-18 | Admin UI configuration | operator views apps, job state, audit and error -> retries safe step | privilege escalation, secret disclosure, drift | App Common UI | browser E2E |
| US-19 | Notification template management | operator versions template -> validation -> app-approved use | missing variable, unsafe HTML, cross-app template | App Common | HTTP + rendering |
| US-20 | Account lock / membership revoke | Collectra blocks account or membership -> current JWT no longer authorizes | token issued earlier, concurrent requests, stale cache | Collectra | HTTP + PostgreSQL |
| US-21 | App secret and key rotation | rotate service secret / Keycloak signing key -> grace period -> retire old | invalid kid, old credential reuse, other realms impacted | App Common + Keycloak | Keycloak + HTTP |
| US-22 | Audit and incident investigation | correlate operator request, provisioning, token policy and notification events | missing correlation, PII leak, tampered logs | App Common | audit query + security |
| US-23 | Account deletion and retention | approved deletion request -> separate app/Keycloak ownership decisions -> retention | another realm same email, legal hold, orphan identity link | Collectra + App Common | DB + security |
| US-24 | Invitation acceptance | Collectra invitation -> verified Keycloak identity -> tenant membership | stolen/replayed invite, wrong email/realm, expired invite | Collectra | browser + PostgreSQL |

## Detailed simulation A — US-02 signup
1. Browser -> Collectra: choose registration; Collectra redirects to Keycloak Collectra realm via OIDC Authorization Code + PKCE.
2. Keycloak: credentials created with email_verified=false; verification link sent by Keycloak-managed SMTP.
3. Before verification, no active Collectra tenant/membership and no access to protected business APIs. Note: Keycloak may issue tokens depending on flow; Collectra must independently enforce verified-email/onboarding gates.
4. User verifies email with Keycloak action token. Browser obtains valid access token; Collectra validates issuer, audience, signature, token class and trusted verified-email status.
5. Collectra creates app-owned onboarding intent keyed by issuer+subject, reserves tenant slug transactionally, provisions local UUID, tenant and membership atomically, records identity link and outbox.
6. Duplicate browser submit/callback returns same result. Partial failure stays retryable without active access.
7. Success criterion: one verified Keycloak identity, one Collectra local UUID/tenant/membership, no business access before ACTIVE.
**Design gap to resolve:** reliable verified-email claim freshness/event reconciliation and first-login UI handling.

## Detailed simulation B — US-09 email
1. Collectra backend obtains audience-restricted Client Credentials token; calls App Common Notification API with recipientRef, template code and idempotency key.
2. App Common validates issuer/client/audience/scope and derives applicationId; persists request and outbox atomically.
3. Worker claims job with lease/fencing; calls Collectra's private recipient resolver using its own audience-restricted identity, checks active recipient and current verified email/notification class.
4. Worker renders versioned allowlisted template, submits to SMTP, records provider acceptance; delivery callback changes state only when trustworthy.
5. Same idempotency key+same request -> same ID; changed request -> 409. Timeout after provider acceptance -> DELIVERY_UNKNOWN; no blind resend.
6. Revoked/changed email before send -> do not send to old address. Another app's machine token cannot resolve Collectra recipients.
**Design gap:** SMTP provider choice and ability to deduplicate ambiguous sends.

## Detailed simulation C — US-11 suspension
1. Operator with fresh MFA requests suspend; App Common records SUSPENDED desired state and durable enforcement operation, returns 202 operationId.
2. Keycloak login/token issuance restrictions, notification submission/queue stop and Collectra resource-server policy update are separately acknowledged.
3. Previously issued valid JWT remains cryptographically valid; Collectra denies only after its local trusted suspension policy is applied.
4. If one enforcement step fails, operation reports PARTIALLY_ENFORCED, alerts and cannot claim fully secured. Stale policy past TTL -> fail closed under approved policy.
5. Re-activation requires independent approval and verified readiness.
**Design gap:** concrete authenticated policy distribution, ack and max staleness SLA.

## Functional coverage model
Each US must link: actor, trigger, preconditions, API/event, owned DB changes, JWT/authorization, UI state, notifications, failure/retry/compensation, security abuse case, observability, executable test ID, evidence SHA. A journey is COMPLETE only if all linked positive and negative tests are GREEN on real infrastructure and UI E2E is GREEN where relevant. A mock-only unit test cannot satisfy an infrastructure acceptance gate.

## Gaps exposed by simulation (new decisions)
- GAP-01: verified-email freshness and pending verification UX after Keycloak signup.
- GAP-02: browser session model (BFF vs public SPA token storage) and CSRF/cookie policy.
- GAP-03: user invitation and tenant provisioning idempotency under concurrent browser requests.
- GAP-04: recipient resolver contract and delivery unknown provider strategy.
- GAP-05: signed suspension policy transport, cache TTL, acknowledgments and fail-closed tradeoff.
- GAP-06: notification template ownership, HTML safety, locale fallback and preview authorization.
- GAP-07: admin UI screens for partial provisioning, failed jobs, audit and retry.
- GAP-08: data deletion/retention across App Common and Keycloak without global identity merging.
- GAP-09: operator bootstrap, break-glass and two-person approval workflow.
- GAP-10: explicit evidence for mobile client only if a real Collectra mobile client exists.
- GAP-11: rate limits and abuse prevention for signup, password recovery, invitations and email.
- GAP-12: backward compatibility plan for existing Collectra HTTP clients and local JWT.

## Mandatory simulation-to-test sequence
Phase A: S2 reviews the 24 traces and gaps, identifies contradictions, approves decisions. Phase B: implement PostgreSQL/Keycloak/HTTP RED fixtures for US-01,02,03,09,11,13,14,20. Phase C: implement minimal GREEN behavior; extend to remaining journeys. Phase D: React browser E2E and SMTP/provider contract tests; fault injection, backup restore, measured performance. Production HOLD until all required P0/P1 journeys GREEN and S2 approval.

## Status
**24 user/system journeys specified; 3 manually reasoned detailed simulations; 12 functional gaps identified. 0 executable journeys demonstrated in this document.** No claim that all functionality is implemented.
