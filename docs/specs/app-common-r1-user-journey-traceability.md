# APP-COMMON-R1 — Traceability matrix: user journeys → APIs → data → tests
Status: DESIGN BASELINE / NOT IMPLEMENTED / NOT EXECUTED
Date: 2026-11-14
Branch: feat/iam-shared-platform

## Interpretation
All 24 US-01..US-24 are mapped to an explicit interface, authoritative state change, and executable acceptance test ID. Endpoints under `/platform`, `/notifications` and `/notification-templates` are proposed App Common contracts; `/collectra` and `/app-b` are consumer-owned **contract targets**, not evidence those APIs currently exist. Keycloak endpoints refer to provider functionality. Every test below is **PLANNED**, not GREEN.

| Story | Scenario | API / interaction | Authoritative state transition | Acceptance test (planned) |
|---|---|---|---|---|
| US-01 | Подключение приложения | `POST /platform/applications; GET /platform/operations/{id}` | applications REGISTERED→CONFIGURING→ACTIVE; provisioning_jobs | `J01-PG-KC: repeat, drift, foreign client, crash` |
| US-02 | Регистрация пользователя | `Keycloak OIDC registration/verify; POST /collectra/onboarding` | Keycloak UNVERIFIED→VERIFIED; Collectra onboarding PENDING→ACTIVE | `J02-BROWSER-PG: unverified denied, concurrent first login` |
| US-03 | Вход и вызов API | `OIDC /authorize,/token; GET /collectra/me` | Keycloak session ACTIVE; app profile ACTIVE | `J03-OIDC-HTTP: iss/aud/azp, expiry, token substitution` |
| US-04 | Refresh и logout | `OIDC /token,/logout; GET /collectra/me` | session ACTIVE→LOGGED_OUT; refresh rotation | `J04-BROWSER: refresh replay, local/global logout` |
| US-05 | Восстановление пароля | `Keycloak account recovery/action-token` | credentials ROTATED; recovery token CONSUMED | `J05-KC-SMTP: expiry, replay, enumeration` |
| US-06 | Изменение email | `Keycloak verify-email/update; app identity projection` | email PENDING_VERIFICATION→VERIFIED; projection version ↑ | `J06-KC-PG: stale update, duplicate event` |
| US-07 | Назначение роли и MFA | `Collectra admin API; OIDC step-up` | local role GRANTED/REVOKED; assurance updated | `J07-HTTP: demotion, stale token, forged role` |
| US-08 | Операторская MFA | `Operator realm OIDC; POST /platform/approvals` | approval REQUESTED→APPROVED/REJECTED | `J08-KC-HTTP: two distinct subjects, fresh MFA` |
| US-09 | Отправка email | `POST /notifications; private recipient resolver` | notification ACCEPTED→QUEUED→SUBMITTED/DELIVERY_UNKNOWN | `J09-PG-SMTP: dedup, recipient changed, crash` |
| US-10 | Статус доставки | `GET /notifications/{id}; POST /provider-events/{provider}` | SUBMITTED→DELIVERED/BOUNCED; provider_events dedup | `J10-WEBHOOK: forged, out-of-order, replay` |
| US-11 | Блокировка приложения | `POST /platform/applications/{id}/suspend; GET /platform/operations/{id}` | ACTIVE→SUSPENDED; enforcement PENDING→ENFORCED/PARTIAL | `J11-KC-HTTP: existing JWT, stale policy, partial ack` |
| US-12 | Возобновление приложения | `POST /platform/applications/{id}/reactivate` | SUSPENDED→CONFIGURING→ACTIVE after checks | `J12-KC-HTTP: drift, failed readiness, unauthorized` |
| US-13 | Второе приложение и SSO | `App-B OIDC /authorize,/token; GET /app-b/me` | same global sub; independent optional app profile | `J13-TWO-APP: no second signup, wrong aud denied, no tenant required` |
| US-14 | Миграция Collectra | `POST /collectra/identity-links (internal verified flow)` | legacy UUID preserved; link VERIFIED→ACTIVE; old sessions revoked | `J14-PG-KC: email collision, rollback, concurrent migration` |
| US-15 | Недоступность Keycloak | `GET /collectra/me; OIDC /authorize` | valid JWT + fresh local policy works; new login unavailable | `J15-FAULT: JWKS cache, unknown kid, fail-closed policy` |
| US-16 | Backup и restore | `Ops restore runbook; reconciliation API` | DB restored; jobs RETRYABLE; audit continuity | `J16-RESTORE: outbox duplicates, signing config mismatch` |
| US-17 | Вывод приложения | `POST /platform/applications/{id}/decommission` | ACTIVE/SUSPENDED→DECOMMISSIONED; shared realm intact | `J17-KC-PG: pending jobs, other clients survive` |
| US-18 | Административный UI | `GET /platform/applications,/operations,/audit` | read models reflect jobs/errors; retry audit event | `J18-BROWSER: RBAC, secrets redaction, safe retry` |
| US-19 | Шаблоны уведомлений | `POST /notification-templates; publish version` | DRAFT→PUBLISHED→RETIRED; immutable version | `J19-HTTP: XSS, locale fallback, cross-app use` |
| US-20 | Отзыв локального доступа | `POST /collectra/users/{id}/block; membership revoke` | Collectra local ACTIVE→BLOCKED; global user remains ACTIVE | `J20-PG-HTTP: existing JWT denied, other app works` |
| US-21 | Ротация ключей/секретов | `Keycloak JWKS rotation; POST /platform/clients/{id}/rotate` | key/secret ACTIVE→GRACE→RETIRED | `J21-KC-HTTP: kid rollover, old secret rejected` |
| US-22 | Аудит инцидента | `GET /platform/audit?correlationId=...` | append-only audit; redacted PII | `J22-PG-HTTP: missing trace, unauthorized query` |
| US-23 | Удаление и retention | `Keycloak account lifecycle; application-specific deletion APIs` | account DELETE_REQUESTED→DELETED; retention/legal hold | `J23-PG-KC: shared account not deleted by one app` |
| US-24 | Приглашение в Collectra | `POST /collectra/invitations; POST /collectra/invitations/{id}/accept` | invite ISSUED→ACCEPTED/EXPIRED; membership created | `J24-BROWSER-PG: replay, wrong subject, tenant boundary` |

## Coverage rules
- Every row requires positive, negative, security, idempotency/retry (where mutating), and observability assertions. Concurrent paths require real PostgreSQL tests; identity paths require real Keycloak; notification paths require fake SMTP with deterministic faults plus provider-contract evidence.
- Test evidence record: `story_id`, `test_id`, `code_commit`, `ci_run_url`, `environment`, `passed_at`, `reviewer`, `result`, `defects`. No story is VERIFIED from design simulation alone.
- P0 release set: US-01,02,03,04,05,07,08,09,11,13,14,20,24. Remaining stories require explicit S2 classification and signoff; no silent omissions.
- Additional acceptance IDs: `SSO-01..SSO-12`, `TEN-01..TEN-10`, `INT-01..INT-08`, `HARD-01..HARD-16`, `SPEC-01..SPEC-10`; map them to primary US tests during test implementation rather than claiming they already run.

## Invariants
1. One shared Keycloak end-user realm, one global `(iss,sub)`, separate OAuth clients and resource audiences.
2. Collectra is the only current consumer requiring tenant isolation; no Collectra tenant required in App-B.
3. App Common is internal control plane; Keycloak OIDC public ingress is explicitly restricted and separate from admin.
4. Global disablement affects all apps; local Collectra block affects only Collectra.
5. A consumer app cannot delete the shared realm, global identity or another app's data.
6. Keycloak issues access tokens; App Common never signs them.
7. Notifications are at-least-once processing, not guaranteed exactly-once email delivery; `DELIVERY_UNKNOWN` is a real state.
8. Never link legacy accounts by email alone; preserve Collectra local UUID and FKs.

## Readiness
24/24 mapped in specification; 0/24 runtime verified; 0/24 evidence URLs; S2 independent approval pending. Production HOLD.