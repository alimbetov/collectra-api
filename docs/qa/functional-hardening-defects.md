# Functional Hardening — Defect Register

Status: WAVE-A ACTIVE
Base audit: main@8704e425ca6ea92d277da136cdfe84d07ebf5057

| ID | Sev | Persona | Surface | Finding | Status | Fix SHA | Verification SHA |
|---|---|---|---|---|---|---|---|
| FH-001 | P1 | Collection Officer | /collections | Router renders PlaceholderPage although Collection backend/spec exists; golden user journey blocked | OPEN | — | — |
| FH-002 | P3 | Platform Super Admin | /platform/audit | Placeholder must not present as completed product per accepted D04 | DEFERRED_ACCEPTED_D04 | — | — |
| FH-003 | P3 | Platform Super Admin | /platform/operations | Placeholder must not present as completed product per accepted D04 | DEFERRED_ACCEPTED_D04 | — | — |
| FH-004 | — | Platform Super Admin | /platform/analytics | Intentionally deferred to VC-9 | DEFERRED_VC9 | — | — |
| FH-005 | P1 | Restricted Tenant User | Customer/Contract/Receivable/Collection APIs | Executable characterization proved USER_READ-only ROLE_HUMAN could read/mutate business core; D01 requires explicit capabilities. Backend guards, permission migration and frontend route/nav gates implemented; verification pending | FIXED | f29fee466980c912802c59a3ba30db808f561a37 | — |
| FH-006 | P2 | Security QA | tenant-owned resources | Paired Alpha/Beta matrix expansion started for business-core path/query/body references; exhaustive domain reconciliation remains | IMPLEMENTING | 8181c4ee589ef9f5f66091bc287326fed335da8a | — |
| FH-007 | P2 | Frontend QA | campaigns/files/imports/integrations/messages/receivables | Many real pages lack direct page-level smoke tests; API/domain tests do not prove browser journey wiring | OPEN_TEST_GAP | — | — |

## Security triage rule

FH-005 becomes P0/P1 only if execution demonstrates unauthorized read/material mutation. If product policy intentionally permits every ROLE_HUMAN actor for a surface, document that decision and downgrade/close the permission-granularity finding. Do not label a vulnerability from annotation inspection alone.

## Closure rules

P0 = cross-tenant/auth bypass/credential exposure/data corruption.
P1 = golden journey blocked, material vertical privilege escalation, incorrect financial/delivery state.
P2 = important workflow/security coverage/recovery/non-disclosure gap.
P3 = UX/diagnostic/product-surface issue.
OPEN -> FIXED requires fix SHA; FIXED -> VERIFIED requires exact-SHA regression evidence. Code inspection alone never closes a defect.
