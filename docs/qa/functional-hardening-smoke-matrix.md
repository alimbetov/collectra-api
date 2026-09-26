# Functional Hardening — Smoke Matrix

Status: ACTIVE
Result vocabulary: NOT_RUN | PASS | FAIL | BLOCKED | WAIVED(reason)

| ID | Persona | Permission/scope | Journey | UI/API target | Expected business result | Negative variants | Automation target | Result |
|---|---|---|---|---|---|---|---|---|
| SM-001 | Platform Super Admin | platform auth | J01 | /platform -> tenants/users/admins | Platform resources navigable without tenant leakage | tenant principal denied | frontend + API integration | NOT_RUN |
| SM-010 | Tenant Admin | identity/admin permissions | J02 | login -> membership/roles | access changes authoritative and re-authorized | missing permission, foreign tenant, revoked user | API integration + frontend | NOT_RUN |
| SM-020 | Integration/Data Manager | integration manage | J03 | service client -> schema -> mapping -> source readiness | ACTIVE source ready for ingestion | stale version, blocked client, foreign tenant | PostgreSQL integration + frontend | NOT_RUN |
| SM-030 | Technical Client | service scope | J10 | source ingestion | one durable logical operation and canonical outcomes | replay, changed-body same key, scope/source denial | PostgreSQL/RabbitMQ | NOT_RUN |
| SM-040 | Operator | customer/receivable | J04 | customer -> invoice/payment | authoritative records visible | foreign tenant, invalid input | integration + frontend | NOT_RUN |
| SM-041 | Operator | receivable mutation | J04 | payment allocation/reversal | authoritative balances update once | duplicate command, mismatch, stale version | PostgreSQL integration | NOT_RUN |
| SM-050 | Collection Officer | collection permissions | J05 | /collections | queue/case lifecycle operational | stale command, terminal transition, foreign tenant | integration + frontend | BLOCKED |
| SM-060 | Content Manager | template manage | J06 | templates/builder | validated published compatible version | invalid placeholder, tenant isolation | integration + frontend | NOT_RUN |
| SM-070 | Campaign Manager | campaign manage/read | J07 | campaign -> run | recipients/messages materialized | duplicate intent, eligibility change, foreign tenant | PostgreSQL/RabbitMQ + frontend | NOT_RUN |
| SM-071 | Campaign Manager | CAMPAIGN_READ | J07 | run -> messages -> detail | masked authoritative delivery state | unknown enums, hierarchy mismatch | frontend + API integration | NOT_RUN |
| SM-080 | Support/Ops | safe diagnostic read | J08 | message diagnostics | safe normalized diagnostic data only | raw PII/body/provider payload absent | security + frontend | NOT_RUN |
| SM-090 | Auditor | read-only grants | J09 | permitted reads | reads succeed, mutations denied | every mutation 403/no state change | authorization integration | NOT_RUN |
| SM-100 | File/Document operator | file permissions | S7 | files registry/upload/download/delete | tenant-safe file lifecycle | foreign file, unsafe metadata, expired/deleted | integration + frontend | NOT_RUN |
| SM-110 | System | delivery worker | S8 | message -> adapter boundary | deterministic state/counters | transient/permanent/timeout/duplicate | RabbitMQ/Testcontainers | NOT_RUN |
| SM-111 | System | delivery worker | S8 | required attachment gate | provider not called until READY | PENDING/FAILED/missing | PostgreSQL/RabbitMQ | NOT_RUN |
| SM-120 | System/Support | recovery | S9 | retry/recovery | no double send/count for non-ambiguous outcomes | worker races, stale events, terminal no-op | concurrency integration | NOT_RUN |
| SM-130 | Malicious/foreign | none/other tenant | S10 | protected resources | no disclosure or state change | BOLA/IDOR, forged tenant IDs | security suite | NOT_RUN |
| SM-140 | Build | n/a | pre-VC9 | clean DB + full CI | reproducible migrations and all gates green | clean bootstrap | CI | NOT_RUN |

## Channel boundary expansion

For each implemented channel create child scenarios:
SUCCESS; PERMANENT_FAILURE; TRANSIENT_FAILURE_THEN_SUCCESS; RETRY_EXHAUSTED; TIMEOUT_BEFORE_ACCEPT; ambiguous accept/timeout where modeled; INVALID_DESTINATION; REQUIRED_ATTACHMENT_PENDING; REQUIRED_ATTACHMENT_FAILED; DUPLICATE_QUEUE_EVENT.

No scenario may be marked PASS merely because a unit test exists. Record the exact automated test/run and verification SHA when executed.
