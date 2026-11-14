# R1.6C.3 — Live infrastructure E2E acceptance contract
Status: NOT EXECUTED. This is a test plan, NOT a PASS report.

## Topology
1. Keycloak 26.x with PostgreSQL 16, one shared realm, one real test user.
2. App Common on a distinct PostgreSQL 16, with issuer, audience, admin client, consumer registry and block/outbox.
3. Collectra API with its own PostgreSQL 16, verified global identity link and real tenant membership.
4. Independent App-B Resource Server, separate process and separate OAuth audience, with durable local revocation and authenticated consumer ACK.

## Preconditions
- Register two mandatory consumers: collectra-api and app-b-api with unique OAuth service-client IDs.
- Provision least-privilege Keycloak admin client, separate read/ack machine clients, and per-application audience.
- Establish verified identity link for Collectra and initialize central ACTIVE version 1.
- Prove all Liquibase migrations, including 054/055, on real PostgreSQL.
- Enable Keycloak mode only in disposable test environment. Do not enable in production.
- Start both consumer pollers, verify local ACTIVE v1 and ACK v1 before user requests.
- Do not log access tokens or service credentials.

## Mandatory test evidence
| ID | Action | Required proof |
| --- | --- | --- |
| E2E-01 | User token A calls Collectra before block | HTTP 200 with tenant membership verified |
| E2E-02 | User token B calls App-B before block | HTTP 200; wrong audience yields 401 |
| E2E-03 | Block global (iss,sub) once | version 2, operation ID, committed outbox |
| E2E-04 | Replay exact token A and token B | Both denied 401/403; no token reissuance needed |
| E2E-05 | Verify Keycloak disable/logout | Refresh fails; admin action evidence |
| E2E-06 | Verify ACKs and operation | Both local DBs at BLOCKED v2, ACK v2; ENFORCED only after both |
| E2E-07 | Duplicate BLOCKED v2, replay ACTIVE v1 | No access restoration |
| E2E-08 | Drop an update, recover network | Polling repairs state; no false ENFORCED before ACK |
| E2E-09 | Stop Keycloak after JWT issuance | Previously issued JWT still denied locally; eventual admin retry |
| E2E-10 | Stop App Common, expire freshness | Both APIs fail closed; availability impact measured |
| E2E-11 | Wrong issuer, forged tenant header, inactive membership | All denied |
| E2E-12 | Legacy Collectra HS256 token during migration | Explicitly document cutover/revocation policy; do not claim globally revoked |

## Hard blockers
- No independent App-B implementation or runnable four-service Docker Compose exists yet.
- User-token login/authorization-code PKCE test fixture and client registration incomplete.
- App Common has no safe initial ACTIVE registration flow and does not yet prove authenticated consumer snapshot application.
- Revocation consumer currently polls at most 10,000 subjects; no pagination or proven SLA.
- No real HTTP test, fault injection, propagation latency, GitHub Actions PASS or independent S2 review has been obtained.

## Release gate
Do not mark R1.6C.3 complete until evidence is attached for all E2E cases and an independent S2 reviewer approves.
