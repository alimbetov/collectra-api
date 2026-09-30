# Local MacBook Functional Assessment

Assessed branch: `audit/local-macbook-functional-assessment`  
Exact SHA: `995fc18d5f5c4d14082f472a0efd7c4ff9f0feeb`  
Date: 2026-09-30  
Verdict: **PARTIAL LOCAL PRODUCT PASS; not release-ready from this run alone**

## Environment

| Component | Evidence |
|---|---|
| macOS arch | `arm64` |
| Java | Temurin `17.0.9` |
| Maven | `3.8.7` |
| Docker | `28.3.3` |
| Compose dependencies | `local_postgres`, `local_rabbitmq`, `local_rustfs` healthy |
| Backend local URL | `http://127.0.0.1:18080` |
| Frontend local URL | `http://127.0.0.1:5173` |
| Frontend Node used | Codex bundled arm64 Node `v24.19.0` with npm CLI `11.12.1` |

## Commands And Gates

| Check | Result | Evidence |
|---|---|---|
| `python3 scripts/check-markdown-links.py` | PASS | 145 links checked |
| `python3 scripts/check-documentation-metadata.py` | PASS | 118 Markdown files checked |
| `mvn --batch-mode --no-transfer-progress spotless:check` | PASS | BUILD SUCCESS |
| `bash scripts/test-slice-07.sh unit` | PASS | 64 tests |
| `mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage` | PASS | 729 tests, JaCoCo gate met |
| `npm ci` | PASS | 162 packages on arm64 Node |
| `npm run typecheck` | PASS | `tsc --noEmit` |
| `npm run test:ci` | PASS | 64 files / 263 tests |
| `npm run build` | PASS | Vite production build; large chunk warning only |

## Local Runtime Evidence

Backend started with:

```bash
SERVER_PORT=18080 \
API_BASE_URL=http://localhost:18080 \
COLLECTRA_PLATFORM_BOOTSTRAP_ENABLED=true \
COLLECTRA_PLATFORM_BOOTSTRAP_EMAIL=super-admin \
COLLECTRA_PLATFORM_BOOTSTRAP_PASSWORD=Alimbetov_Ruslan \
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Runtime results:

| Check | Result |
|---|---|
| Liquibase | database up to date, 60 total changesets |
| Health | `UP`; db `UP`; rabbit `UP` |
| Platform login | `POST /api/v1/platform/auth/login` -> 200 |
| Platform `/me` | `GET /api/v1/platform/me` -> 200 |
| Tenant register | `POST /api/v1/auth/tenants/register` -> 201 |
| Tenant login by slug | `POST /api/v1/auth/login/by-slug` -> 200 |
| Restart durability | backend graceful stop/start; health/customer/invoice/campaign reads -> 200 |

Operational prerequisite: `super-admin / Alimbetov_Ruslan` is a platform account only. It requires platform bootstrap to be enabled and the frontend `Платформа` login mode. It must not be used in the tenant slug/email login form.

## Golden Journey Runtime Smoke

Tenant created:

```text
slug: codex-audit-20260930
admin: admin+audit20260930@collectra.local
```

| Step | Endpoint | Result |
|---|---|---|
| Customer | `POST /api/v1/customers` | 201 |
| Primary email | `POST /api/v1/customers/{id}/emails` | 201 |
| Contract | `POST /api/v1/contracts` | 201 |
| Invoice | `POST /api/v1/invoices` | 201 |
| Payment | `POST /api/v1/payments` | 201 |
| Allocation | `POST /api/v1/payments/{id}/allocations` | 201 |
| Invoice after allocation | `GET /api/v1/invoices/{id}` | `PARTIALLY_PAID`, paid `25000`, outstanding `100000` |
| Collection case | `POST /api/v1/collection-cases` | 201 |
| Collection start | `POST /api/v1/collection-cases/{id}/start` | 200 |
| Promise | `POST /api/v1/collection-cases/{id}/promises` | 201 |
| Template create | `POST /api/v1/templates` | 201 after uppercase code correction |
| Template version | `POST /api/v1/templates/{id}/versions` | 201 |
| Template validate | `POST /api/v1/templates/versions/{id}/validate` | 200 valid |
| Template publish | `POST /api/v1/templates/versions/{id}/publish` | 200 after fresh revision |
| Campaign create | `POST /api/v1/campaigns` | 201 |
| Campaign validate | `POST /api/v1/campaigns/{id}/validate` | 200 valid |
| Campaign preview | `POST /api/v1/campaigns/{id}/preview` | 200 after manual tenant locale seed |
| Campaign activate | `POST /api/v1/campaigns/{id}/activate` | 200 |
| Campaign prepare run | `POST /api/v1/campaigns/{id}/runs` | 201, 1 recipient |
| Recipients | `GET /api/v1/campaigns/{id}/runs/{runId}/recipients` | 200, 1 `SNAPSHOT` |
| Messages | `GET /api/v1/campaigns/{id}/runs/{runId}/messages` | 200, empty list |

Important boundary: this local API journey reached campaign run preparation and recipient snapshot. It did not prove terminal message delivery in the running product; that remains covered by repository integration tests and prior PC evidence, not by this manual local runtime pass.

## Negative And Security Evidence

| Scenario | Result |
|---|---|
| Cross-tenant read with tenant B token against tenant A customer | 404 |
| Platform token against tenant customer API | 403 |
| Template invalid placeholder | 200 validation response with `valid=false`, `UNKNOWN_PLACEHOLDER` |
| Template publish with stale revision | 409 `VERSION_CONFLICT` |
| Customer stale update | 409 |

## UI Findings

Frontend was started on `http://127.0.0.1:5173` with backend on `18080`.

| Check | Result |
|---|---|
| Login page render | PASS |
| Tenant/platform mode switch visible | PASS |
| Tenant form labels | PASS |
| Tenant login through UI | FAIL in local non-default port setup |

Root cause observed: API login works by curl, but UI login fails when frontend is served from `5173` and backend from `18080`. Current Vite config uses one `API_BASE_URL` both as dev proxy target and as build-time browser API base. On non-default backend ports this tends to force browser absolute cross-origin calls instead of same-origin `/api` proxy calls. Backend API itself is healthy.

## Defects

| Severity | ID | Finding | Reproduction |
|---|---|---|---|
| HIGH | LMA-001 | Fresh tenant registration does not create an enabled default tenant locale. Campaign preview fails with `Tenant has no enabled default locale` until manual DB seed is added. | Register tenant, create customer/email/template/campaign, call `POST /api/v1/campaigns/{id}/preview`; observe 409. |
| HIGH | LMA-002 | Local frontend cannot complete tenant login when backend runs on a non-default port via `API_BASE_URL`; dev proxy and browser absolute API base are coupled. | Start backend on `18080`, start frontend with `VITE_API_BASE_URL=http://127.0.0.1:18080`, submit tenant login; UI shows `Не удалось войти` while curl login returns 200. |
| MEDIUM | LMA-003 | Template placeholder validation lowercases camelCase placeholder names; `{{invoice.outstandingAmount}}` becomes `invoice.outstandingamount` and fails catalog lookup. | Create template version with `invoice.outstandingAmount`, validate; response has `UNKNOWN_PLACEHOLDER`. |
| MEDIUM | LMA-004 | `/api/v1/platform/me` returns only `userId`; platform UI/header cannot show email/display/role without additional calls. | Platform login then `GET /api/v1/platform/me`; response keys only `userId`. |
| LOW | LMA-005 | Vite production build emits a >500 kB chunk warning. | `npm run build`. |

## CAP-01..CAP-20 Matrix

| ID | Local assessment status |
|---|---|
| CAP-01 | PASS API: registration/login/session token issue. UI login blocked by LMA-002. |
| CAP-02 | PASS API: customer/contact create/read/update conflict. |
| CAP-03 | PASS API: contract create/read. |
| CAP-04 | PASS API: invoice create/read/status after allocation. |
| CAP-05 | PASS API: payment create/allocation. Reversal not manually executed. |
| CAP-06 | PARTIAL API: case/start/promise covered; dispute/action/timeline not manually executed. |
| CAP-07 | PASS API: template create/version/validate/publish; placeholder issue recorded. |
| CAP-08 | PARTIAL API: campaign create/validate/preview/activate/prepare covered after manual locale seed. |
| CAP-09 | PARTIAL: recipients snapshot covered; local messages list empty after prepare. |
| CAP-10 | SKIPPED local manual: file/document lifecycle covered by automated verify only in this pass. |
| CAP-11 | PARTIAL: admin permissions observed; full persona role matrix not manually completed. |
| CAP-12 | SKIPPED local manual: profile/password/session UI not traversed. |
| CAP-13 | SKIPPED local manual: source schema/mapping UI not traversed. |
| CAP-14 | PASS automated only: import/diagnostics covered in `clean verify`; no manual upload in this pass. |
| CAP-15 | PASS automated only: service client flows covered by verify; no manual credential flow in this pass. |
| CAP-16 | SKIPPED local manual: dashboard not traversed due UI login blocker. |
| CAP-17 | PASS automated only: analytics covered by verify; no manual dashboard check. |
| CAP-18 | PASS API: platform login/me; platform admin UI not traversed. |
| CAP-19 | OUT_OF_SCOPE for production providers; local simulated boundary only. |
| CAP-20 | PARTIAL: restart/durable reads covered; full worker recovery not manually forced. |

## G01-G18 And F01-F14 Matrix

| Range | Local result |
|---|---|
| G01 | PASS: tenant created and isolated. |
| G06-G10 | PASS/PARTIAL: customer, invoice, payment allocation and collection promise covered. |
| G11-G12 | PASS/PARTIAL: published template and campaign run preparation covered. |
| G13-G17 | SKIPPED local manual; automated `clean verify` passed, but this run did not manually prove generated attachment, terminal delivery, or analytics projections. |
| G18 | PASS: cross-tenant read denied as 404. |
| F01-F12 | PASS automated only through backend verify; not manually fault-injected here. |
| F13 | PASS local: cross-tenant denial 404 and platform-vs-tenant 403. |
| F14 | SKIPPED local manual: disabled/revoked identity not performed to avoid locking the only tenant admin; requires dedicated invited persona. |

## PC-01..PC-20 Matrix

Current repository docs mark PC-01..PC-20 verified by prior exact-SHA CI. This local assessment does not re-certify every PC row as runtime-manual VERIFIED.

| PC range | Local status |
|---|---|
| PC-01..PC-03 | PASS/PARTIAL local API for customer/invoice/payment/collection. |
| PC-04..PC-08 | PARTIAL local API for template/campaign/recipient snapshot; terminal message materialization not observed. |
| PC-09..PC-14 | PASS automated only; not manually forced in runtime. |
| PC-15 | PASS local negative checks. |
| PC-16 | BLOCKED by LMA-002 for local UI traversal. |
| PC-17 | PASS startup/upgrade on existing local DB; clean empty DB bootstrap not repeated in this pass. |
| PC-18..PC-20 | PASS automated only from `clean verify`; this manual run is not a final exact-SHA release gate. |

## Persona/RBAC Matrix

| Persona | Status |
|---|---|
| Platform super-admin | PASS API login; `/platform/me` minimal response. |
| Tenant Admin | PASS API; 44 permissions observed. |
| Receivables | NOT MANUALLY EXECUTED as separate user. |
| Collections | NOT MANUALLY EXECUTED as separate user. |
| Campaign | NOT MANUALLY EXECUTED as separate user. |
| Integration | NOT MANUALLY EXECUTED as separate user. |
| Auditor | NOT MANUALLY EXECUTED as separate user. |

Reason: current public self-service flow creates a tenant admin. Separate persona checks require invitation/activation or deterministic test-user provisioning. That should be the next local audit pass after fixing or documenting activation fixtures.

## Local Bootstrap And Reproducibility Findings

- Local platform bootstrap is intentionally env-gated. Staging runbooks must explicitly use platform login mode for `super-admin`.
- Fresh tenant registration does not seed tenant locales, but campaign/template preview requires an enabled default locale. Manual local DB seed used:

```sql
insert into tenant_locales(id, tenant_id, locale, enabled, is_default, sort_order)
values ('22222222-3333-4444-8555-666666666666', '<tenant-id>', 'ru', true, true, 10)
on conflict (tenant_id, locale)
do update set enabled=true, is_default=true, sort_order=10;
```

## Tests Discovered But Not Executed Manually

| Area | Reason |
|---|---|
| Full UI journeys after login | Blocked by LMA-002 local frontend/backend configuration. |
| Separate role personas | Requires invited/activated users or seeded persona accounts. |
| File upload/manual import | Automated verify passed; manual UI/API upload not run in this pass. |
| Generated PDF attachment lifecycle | Automated verify passed; manual end-to-end attachment generation not run. |
| RabbitMQ worker terminal delivery recovery | Automated verify passed; manual failure injection not run. |
| Live external providers | Out of scope for pre-provider local boundary. |

## Readiness

| Readiness level | Verdict |
|---|---|
| Feature-complete | **Mostly yes by automated evidence; local runtime found onboarding/config gaps.** |
| Staging/UAT-ready | **Not yet** until LMA-001 and LMA-002 are fixed or explicitly worked around in staging instructions. |
| Release-ready | **Not from this run alone**; manual UI/persona/delivery runtime gaps remain. |
| Production-ready | **No**; live provider, deployment/IaC, backup/restore/DR, load, SLO and production operations evidence are outside this run. |
