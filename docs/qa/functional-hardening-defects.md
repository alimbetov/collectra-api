# Functional Hardening — Defect Register

Status: WAVE-A ACTIVE
Base audit: main@8704e425ca6ea92d277da136cdfe84d07ebf5057

| ID | Sev | Persona | Surface | Finding | Status | Fix SHA | Verification SHA |
|---|---|---|---|---|---|---|---|
| FH-001 | P1 | Collection Officer | /collections | Placeholder replaced with permission-aware queue/case workspace using existing lifecycle/history APIs; verification pending | FIXED | b4ada0382473f8febce825a00d5673f1ec8717e4 | — |
| FH-002 | P3 | Platform Super Admin | /platform/audit | Deferred surface removed from current navigation/router per D04 | FIXED | adf3993574cf5eb4c4b954a8320c29e9b1d5040e | — |
| FH-003 | P3 | Platform Super Admin | /platform/operations | Deferred surface removed from current navigation/router per D04 | FIXED | adf3993574cf5eb4c4b954a8320c29e9b1d5040e | — |
| FH-004 | — | Platform Super Admin | /platform/analytics | Intentionally deferred to VC-9 | DEFERRED_VC9 | — | — |
| FH-005 | P1 | Restricted Tenant User | Customer/Contract/Receivable/Collection APIs | Executable characterization proved USER_READ-only ROLE_HUMAN could read/mutate business core; D01 requires explicit capabilities. Backend guards, permission migration and frontend route/nav gates implemented; verification pending | FIXED | f29fee466980c912802c59a3ba30db808f561a37 | — |
| FH-006 | P2 | Security QA | tenant-owned resources | Alpha/Beta path/query/body matrix plus focused file/import/campaign/message tenant-isolation suites now cover the pre-VC9 tenant-owned domain inventory; final exact-SHA verification pending | FIXED | 8181c4ee589ef9f5f66091bc287326fed335da8a | pending final exact-SHA gate |
| FH-007 | P2 | Frontend QA | primary frontend routes | Executable router contract now traverses every primary pre-VC9 route pattern; permission/auth deep-link guards and domain page suites cover routing, URL state, forbidden handling and key workflows | FIXED | 9ccb04932000000f0a193f32f125a596f0b07be3 | pending final exact-SHA gate |

| FH-008 | P0 | Platform / Release | shared application configuration | A platform-bootstrap credential was committed in shared configuration with bootstrap enabled. Current config now disables shared bootstrap and prod accepts credential only from environment; because the value exists in Git history, any environment that ever used it requires external rotation before production readiness | FIXED_CODE_ROTATION_REQUIRED | c1f23d573b0b45ed0aba4df538d6e7002c391ef1 | — |\n| FH-009 | P1 | Async document generation | duplicate generation event / concurrent worker claim | A duplicate broker event could observe an already PROCESSING job, throw, and drive listener retry that reset the active owner's job to PENDING; the original owner could then fail completion. Duplicate claims are now idempotent no-ops under the existing row lock and concurrency regression asserts one owner | FIXED | 72a57df55604b0c484266fa32044d2fa271ffca5 | pending final exact-SHA gate |\n| FH-010 | P2 | Collection Officer | /collections/:caseId child commands | Promise/dispute/action transition errors were omitted from the page mutation error channel, so authorization/conflict failures could be invisible instead of following 403/ProblemDetail reconciliation semantics. Child command errors now participate in the common fail-closed UI path with regression coverage | FIXED | e3598ba10e8081d2806ef23f23d1fd3c536ea1d1 | pending final exact-SHA gate |

| FH-011 | P2 | Integration/Data Manager | workspace navigation `/integrations` | Main navigation required `SERVICE_CLIENT_READ` even though Integration Setup Center supports four independent read capabilities; legitimate source/schema/mapping operators could use deep links but lost the product entry point. Navigation now uses any-of integration read capabilities with regression coverage | FIXED | e0efd4a158072a28dac170ecc3683445b85c0d56 | pending final exact-SHA gate |

## Security triage rule

FH-005 becomes P0/P1 only if execution demonstrates unauthorized read/material mutation. If product policy intentionally permits every ROLE_HUMAN actor for a surface, document that decision and downgrade/close the permission-granularity finding. Do not label a vulnerability from annotation inspection alone.

## Closure rules

P0 = cross-tenant/auth bypass/credential exposure/data corruption.
P1 = golden journey blocked, material vertical privilege escalation, incorrect financial/delivery state.
P2 = important workflow/security coverage/recovery/non-disclosure gap.
P3 = UX/diagnostic/product-surface issue.
OPEN -> FIXED requires fix SHA; FIXED -> VERIFIED requires exact-SHA regression evidence. Code inspection alone never closes a defect.
