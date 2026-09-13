# Slice 10A — User and Security Scenario Matrix

Status: NORMATIVE TEST MATRIX
Parent spec: `docs/specs/slice-10a-multichannel-mock-smoke-lab.md`

## 1. Goal

Extend Slice 10A from provider smoke tests into a deterministic regression laboratory that validates complete user journeys, operational behaviour and security controls through the real Collectra pipeline.

Target: **at least 30 meaningful variations for each critical process**. Prefer parameterized tests, reusable fixtures, deterministic fault injection and fixed seeds over duplicated test methods.

## 2. Personas

- Tenant Admin
- Integration Client
- Campaign Manager
- Collection / Customer-Care Operator
- Support / Ops
- Recipient
- Unauthorized / malicious client

## 3. Global invariants

1. Tenant isolation is mandatory for read and write paths.
2. Terminal message states never move backwards because of stale events.
3. Duplicate broker events never double-send or double-count.
4. `attemptCount` counts actual provider-boundary calls only.
5. `retryCount` changes only on the defined retry transition.
6. Message transition and CampaignRun counters obey the same transaction/locking contract.
7. Required attachments gate provider invocation.
8. Mock mode never performs external network I/O.
9. Unknown provider/mode fails closed.
10. No credentials, raw PII, message bodies or attachment content appear in operational logs.
11. Test Clock, provider scenarios and fault injection are deterministic.
12. One physical delivery uses a stable idempotency/delivery key across retries.
13. Provider `accepted but response lost` is an explicit ambiguous outcome (`UNKNOWN` or equivalent), never a blind resend.
14. Recovery goes through the same transition/counter rules as normal delivery.
15. Ownership of a delivery attempt is atomic.
16. Test-only scenario selectors/fault hooks are inaccessible in production.
17. Client-supplied IDs are always re-scoped by authenticated tenant context; path/body tenant IDs are never trusted as authorization.
18. Security validation failures do not reveal whether foreign-tenant resources exist.

## 4. Process P01 — Tenant/project/channel configuration: 30 variations

Cover: valid per-channel mock/provider modes, mixed channel modes, missing/unknown provider, invalid mode, production mock rejection, concurrent updates, config version conflicts, invalid timeout/backoff/rate profile, environment override precedence, restart persistence, secret masking, safe configuration diagnostics, cross-tenant config access, unauthorized config mutation, disabled/re-enabled channels, queued work during disablement, malformed YAML/properties, fail-fast behaviour, and operator-safe effective-config view.

## 5. Process P02 — Import/API ingestion: 30 variations

Cover JSON/XML/CSV/XLSX, canonical/custom fields, header aliases, case normalization, missing required fields, invalid date/decimal/currency, duplicate external IDs, replayed upload, multiple emails/phones, cardinality overflow, nested recipients, RU/KZ/CJK, empty/corrupt files, large bounded imports, one bad row among valid rows, tenant-scoped uniqueness, partial-vs-atomic import semantics, malicious formula-like spreadsheet cells, CSV injection strings, XML entity expansion/XXE rejection, oversized payloads, and unsupported MIME/extension combinations.

## 6. Process P03 — Template/materialization/placeholders: 30 variations

Cover five channel variants, required/optional/unknown placeholders, all major placeholder namespaces, HTML escaping, plain-text handling, RU/KZ/CJK, amount/currency/date/timezone formatting, large body, empty body, subject boundaries, immutable materialized version, special characters, control characters, malicious HTML/script payloads, template expression injection attempts, unsupported placeholder syntax, and tenant-safe template lookup.

## 7. Process P04 — Campaign lifecycle and eligibility: 30 variations

Cover create/start/pause/resume/cancel, empty/one/100-recipient runs, duplicate start, concurrent start, client timeout after commit, payment/partial-payment eligibility recheck, customer state change, missing channel destination, multi-channel recipient, provider outage isolated per channel, materialization failure isolated per recipient, intentional rerun, progress view, pending retries before completion, cancel-before-claim, cancel-during-processing race, tenant B trying to operate tenant A campaign, unauthorized role actions, and stale UI/API commands after state changes.

## 8. Process P05 — Channel routing/provider boundary: 30 variations

Cover success for EMAIL/SMS/WHATSAPP/TELEGRAM/IN_APP, exact adapter isolation, unsupported channel, missing adapter, invalid destination, empty body/subject, Unicode and long SMS, WhatsApp template rejection, Telegram target errors, expired push target, 400/401/403/429/500/502/503 mappings, connection refusal, timeout-before-accept, delayed success, malformed response, 200 without required provider id, runtime exception, and slow provider.

## 9. Process P06 — Attachments/document generation/storage: 30 variations

Cover no attachment, optional/required READY, required PENDING/FAILED, multiple attachments, generated PDF, generation PENDING/FAILED, object missing, zero-byte, corrupt bytes, MIME mismatch, expired/deleted object, storage timeout/refusal/permission error, object disappearing after readiness check, immutable retry references, large allowed file, oversize file, unsupported type, Unicode filename, path traversal filename, cross-tenant attachment ID, same attachment reused by two messages, CJK PDF generation, provider failure after successful resolution.

## 10. Process P07 — Retry/backoff/attempt accounting: 30 variations

Cover success first try, 1/2/N transient failures then success, exact retry exhaustion, permanent no-retry, 429 Retry-After, 5xx backoff, timeout/refusal, restart during RETRY_WAIT, duplicate retry scheduling, stale retry after SENT/FAILED/CANCELLED, queue redelivery without provider call, failed ownership CAS, pre-provider validation failures, exact boundary of nextAttemptAt, controlled Clock, backoff cap, deterministic jitter, batch retry behaviour, and safe support API representation.

## 11. Process P08 — Duplicate/idempotency/concurrency/ambiguous outcome: 30 variations

Cover sequential duplicate, 100 duplicates, 2 and 8 worker races, 8 workers + 100 queue records, duplicate after terminal states, simultaneous retry events, crash AFTER_CLAIM, BEFORE_PROVIDER, AFTER_PROVIDER_ACCEPTED, timeout-after-accept, provider dedup by delivery key, DB failures before/after provider, optimistic-lock conflict, transaction rollback, recovery-vs-worker, recovery-vs-retry, all three racing, stale worker returns after cancel/SENT, stable key across retries, new key for new logical delivery, separate keys per channel, tenant isolation of keys, and fixed-seed fuzz/property sequences.

## 12. Process P09 — Recovery/stuck/dead/manual operations: 30 variations

Cover stale threshold below/exact/above boundary, stale PROCESSING with/without attempts left, terminal no-op, two recovery workers, repeated recovery batch, application restart, controlled Clock, authorized manual retry, forbidden retry of SENT/CANCELLED, unauthorized manual retry, cross-tenant retry, pause/resume interaction, cancel QUEUED/RETRY_WAIT/PROCESSING, cancel while provider in flight, cancel after provider accepted, stuck/dead metrics, recovery metric cleanup, partial row failure inside batch, large bounded recovery batch, lock order `Message -> CampaignRun`, and safe recovery audit history.

## 13. Process P10 — API/dashboard/counters/observability: 30 variations

Cover tenant-scoped paging, stable sorting, filters status/channel/customer, combined filters, masked email/phone, detail view, missing resource, foreign resource, SENT/FAILED/RETRY_WAIT counters, retry not counted as terminal failure, duplicate terminal update no double-count, partial multi-channel outcomes, metrics for retry/stuck/dead, bounded-cardinality labels, safe provider error normalization, correlation IDs, retry correlation continuity, no body/attachment logging, alert on auth/provider config failures, alert de-duplication, and consistency between API, DB state and metrics.

## 14. Process P11 — Security abuse and authorization: at least 30 variations

These are mandatory implementation tests, not documentation-only checks.

| ID | Security scenario | Expected result |
|---|---|---|
| S01 | Request without authentication | 401; no resource existence leak |
| S02 | Invalid JWT/signature | 401; safe error |
| S03 | Expired JWT | 401 |
| S04 | JWT for Tenant A requests Tenant B message by ID | 404/403 per policy; no foreign data |
| S05 | Tenant A requests Tenant B campaign | Same non-disclosure rule |
| S06 | Tenant A requests Tenant B customer | Same non-disclosure rule |
| S07 | Tenant A references Tenant B attachment ID | Rejected before resolver/provider |
| S08 | Body contains forged `tenantId` different from authenticated tenant | Authenticated tenant scope wins/rejects |
| S09 | Query/path tenant parameter is manipulated | Cannot escape authenticated scope |
| S10 | User with read-only role tries retry/cancel | 403/no state change |
| S11 | User with operator role tries admin config mutation | 403 |
| S12 | Support role reads raw destination when only masked view is allowed | Masked/denied |
| S13 | ID enumeration/BOLA over message IDs | No foreign resource disclosure |
| S14 | ID enumeration over campaign IDs | No foreign disclosure |
| S15 | ID enumeration over attachment IDs | No foreign disclosure |
| S16 | SQL injection payload in filters/search fields | Parameterized query; no query alteration |
| S17 | Template placeholder/expression injection | Treated as data or rejected by grammar |
| S18 | HTML/script payload in customer/custom fields | Properly escaped for HTML templates |
| S19 | CRLF/log injection in destination/provider error | Logs remain single structured events/sanitized |
| S20 | Path traversal filename (`../../...`) | Sanitized/not used as filesystem path |
| S21 | XML external entity payload | XXE disabled/rejected |
| S22 | Billion-laughs/entity-expansion payload | Parser limits/rejection |
| S23 | CSV/Excel formula injection (`=CMD(...)`, `+`, `-`, `@`) | Export/import policy neutralizes dangerous spreadsheet formulas where applicable |
| S24 | Oversized HTTP payload | 413/validation before memory pressure |
| S25 | Excessive page size | Capped/rejected |
| S26 | Excessive filter cardinality/query abuse | Bounded validation |
| S27 | Repeated manual retry abuse | Authorization + rate/command guard prevents amplification |
| S28 | Replay of same technical-client request with idempotency key | No duplicate logical delivery |
| S29 | Same idempotency key used with different payload | Conflict/rejected, never silently accepted as same operation |
| S30 | Brute-force requests against resource IDs | No data leak; rate protection/observability where configured |
| S31 | Provider credential accidentally included in exception | Redacted |
| S32 | Authorization header logged | Must never appear |
| S33 | Raw email/phone logged | Must not appear in operational logs |
| S34 | Message body logged | Must not appear unless explicitly safe/debug-disabled policy allows, default forbidden |
| S35 | Attachment content/base64 logged | Forbidden |
| S36 | Mock scenario header/fixture selector supplied to production endpoint | Ignored/rejected; production cannot enable test behaviour |
| S37 | Fault-injection control invoked outside test/local profile | Bean/endpoint absent, not merely unauthorized |
| S38 | `provider=mock` in prod profile | Application startup fails |
| S39 | Unknown provider causes fallback | Forbidden; fail fast |
| S40 | SSRF-like destination/provider URL supplied through user data | User input cannot redefine provider endpoint |
| S41 | Open redirect/deep-link abuse in In-App custom data | Validated against application policy if deep links are constrained |
| S42 | Malicious Unicode/control chars in recipient/template fields | Safely normalized/preserved without log/header injection |
| S43 | Race: authorization revoked between UI load and command | Command re-authorizes at execution time |
| S44 | Deleted/disabled user reuses stale token | Current security policy blocks where revocation/session model supports it |
| S45 | Cross-tenant cache contamination | Cache keys include tenant scope |

## 15. Security implementation requirements

- Every repository/service query handling tenant data must include tenant scope at data-access boundary, not rely only on controller filtering.
- Never authorize solely by checking an ID supplied by the client.
- Prefer object lookup by `(tenantId, id)` or equivalent tenant-scoped repository method.
- Security-sensitive commands (`retry`, `cancel`, config mutation) require explicit authorities and tenant ownership.
- Provider credentials must come from externalized secret configuration; never store credentials in Git or expose them through actuator/config APIs.
- Mock/fault-injection infrastructure must be conditionally created only for test/local profiles and must not be routable from production user input.
- Logs use structured safe fields: messageId, campaignRunId, tenant-safe internal IDs, channel, state, normalized error code. Raw body, raw destination, auth headers, tokens, credentials and attachment content are forbidden.
- Metrics must use bounded labels. Never use email, phone, messageId, customerId, providerReference or arbitrary error text as labels.
- XML parsers must disable external entities/DTD where not required and enforce parser limits.
- CSV/XLSX processing must define spreadsheet-formula handling for any content later exported/opened by users.
- File names are metadata only; storage object keys are generated server-side.
- Provider base URLs/endpoints are configuration, never derived from recipient/template/import data.
- Request payload/page/file size limits must be explicit and tested.
- Error responses must be normalized and avoid stack traces/internal class names/secrets.

## 16. Local MacBook execution model

Recommended commands:

```bash
./mvnw test
./mvnw verify -Psmoke-lab
./mvnw verify -Psmoke-lab,stress
./mvnw verify -Psmoke-lab,security
```

`smoke-lab` should run deterministic end-to-end channel/user flows. `stress` adds concurrency/recovery races. `security` adds authorization/abuse/parser/logging/config safety tests. Profiles may be combined.

PostgreSQL and RabbitMQ may use Testcontainers/Docker Desktop. No external provider network call is allowed from mock/security suites.

## 17. Test architecture

Recommended reusable components:

- `MockProviderScenarioRegistry`
- `MockProviderInbox`
- `DeliveryFaultInjector`
- `MutableTestClock`
- `SecurityTestPrincipalFactory`
- `TenantFixtureFactory`
- `CampaignScenarioFactory`
- `AttachmentFixtureFactory`
- `LogCaptureAssertions`
- `MetricAssertions`

Deterministic provider outcomes should include at least:

`SUCCESS`, `PERMANENT_FAILURE`, `TRANSIENT_FAILURE`, `RATE_LIMIT`, `TIMEOUT_BEFORE_ACCEPT`, `ACCEPT_THEN_TIMEOUT`, `MALFORMED_RESPONSE`, `SLOW_SUCCESS`, `CONNECTION_REFUSED`, `UNAUTHORIZED_PROVIDER`, `FORBIDDEN_PROVIDER`.

Fault points should include:

`AFTER_CLAIM`, `BEFORE_PROVIDER`, `AFTER_PROVIDER_ACCEPTED`, `BEFORE_STATE_COMMIT`, `AFTER_STATE_COMMIT`.

## 18. Merge gates

Slice 10A is not merge-ready until:

1. All five channels traverse the real worker/router/state-machine path to the mock boundary.
2. Critical processes have >=30 meaningful deterministic variations or an equivalent parameterized matrix.
3. Duplicate/concurrency/recovery tests prove no accidental double-send for non-ambiguous outcomes.
4. Accept-then-timeout/crash has an explicit safe idempotency/UNKNOWN contract.
5. Attachments, counters, retries and recovery are validated against PostgreSQL state.
6. Tenant isolation and BOLA/IDOR tests are green.
7. PII/secrets/logging/metrics tests are green.
8. Mock/fault infrastructure cannot activate in production.
9. Parser/file security cases are green.
10. Local `mvn verify -Psmoke-lab,security` is reproducible on MacBook and performs no external network calls.
