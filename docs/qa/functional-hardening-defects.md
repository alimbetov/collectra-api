# Functional Hardening — Defect Register

Status: ACTIVE
Base audit: main@8704e425ca6ea92d277da136cdfe84d07ebf5057

| ID | Sev | Persona | Journey | Route/API | Expected | Actual | Status | Fix SHA | Verification SHA |
|---|---|---|---|---|---|---|---|---|---|
| FH-001 | P1 | Collection Officer | J05 | /collections | Real collection work queue and case workspace | Router renders PlaceholderPage although collection backend/spec exists | OPEN | — | — |
| FH-002 | P3 | Platform Super Admin | J01 | /platform/audit | Explicit supported product surface or explicit exclusion | Placeholder route | NEEDS_DECISION | — | — |
| FH-003 | P3 | Platform Super Admin | J01 | /platform/operations | Explicit supported product surface or explicit exclusion | Placeholder route | NEEDS_DECISION | — | — |
| FH-004 | — | Platform Super Admin | VC-9 | /platform/analytics | Analytics scope handled by VC-9 | Placeholder intentionally remains until VC-9 | DEFERRED_VC9 | — | — |

## Rules

- Add a row for every reproducible deviation.
- P0: security/data corruption/cross-tenant.
- P1: golden journey blocked or incorrect financial/delivery state.
- P2: important workflow, deep-link, recovery or error-handling defect.
- P3: UX/diagnostic defect without incorrect business state.
- OPEN -> FIXED requires a fix SHA.
- FIXED -> VERIFIED requires regression evidence on an exact verification SHA.
- VERIFIED/CLOSED is forbidden based only on code inspection.
