# Functional Hardening — Defect Register

Status: WAVE-A ACTIVE
Base audit: main@8704e425ca6ea92d277da136cdfe84d07ebf5057

| ID | Sev | Persona | Surface | Finding | Status | Fix SHA | Verification SHA |
|---|---|---|---|---|---|---|---|
| FH-001 | P1 | Collection Officer | /collections | Router renders PlaceholderPage although Collection backend/spec exists; golden user journey blocked | OPEN | — | — |
| FH-002 | P3 | Platform Super Admin | /platform/audit | Placeholder; product contract unresolved | NEEDS_DECISION | — | — |
| FH-003 | P3 | Platform Super Admin | /platform/operations | Placeholder; product contract unresolved | NEEDS_DECISION | — | — |
| FH-004 | — | Platform Super Admin | /platform/analytics | Intentionally deferred to VC-9 | DEFERRED_VC9 | — | — |
| FH-005 | P1 candidate | Restricted Tenant User | Customer/Contract/Receivable/Collection APIs | Controllers expose broad ROLE_HUMAN boundary rather than capability permissions. Tenant predicates prevent horizontal crossover in inspected services, but vertical mutation policy is not demonstrably enforced | SECURITY_REVIEW | — | — |
| FH-006 | P2 | Security QA | tenant-owned resources | Cross-tenant negative tests exist for several domains but no single exhaustive paired-tenant BOLA matrix covers every path/query/body foreign reference | OPEN_TEST_GAP | — | — |
| FH-007 | P2 | Frontend QA | campaigns/files/imports/integrations/messages/receivables | Many real pages lack direct page-level smoke tests; API/domain tests do not prove browser journey wiring | OPEN_TEST_GAP | — | — |

## Security triage rule

FH-005 becomes P0/P1 only if execution demonstrates unauthorized read/material mutation. If product policy intentionally permits every ROLE_HUMAN actor for a surface, document that decision and downgrade/close the permission-granularity finding. Do not label a vulnerability from annotation inspection alone.

## Closure rules

P0 = cross-tenant/auth bypass/credential exposure/data corruption.
P1 = golden journey blocked, material vertical privilege escalation, incorrect financial/delivery state.
P2 = important workflow/security coverage/recovery/non-disclosure gap.
P3 = UX/diagnostic/product-surface issue.
OPEN -> FIXED requires fix SHA; FIXED -> VERIFIED requires exact-SHA regression evidence. Code inspection alone never closes a defect.
