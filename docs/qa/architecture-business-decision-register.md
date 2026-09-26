# Architecture / Business Decision Register

Status: OPEN FOR WORKSHOP
Rule: Codex gathers evidence and recommendation; product owner accepts the business rule before policy-dependent remediation.

| ID | Topic | Current evidence / problem | Architect recommendation | Consequences | Alternatives | Decision |
|---|---|---|---|---|---|---|
| D01 | Business-core RBAC | Customer/Contract/Receivable/Collection expose broad ROLE_HUMAN boundaries in Wave-A inspection | Capability READ/MANAGE authorization composed into personas; server-side enforcement | migration/seed + controller/API/frontend permission updates + regression matrix | retain all-human access if explicitly intended | REQUIRED |
| D02 | Collections Workspace | backend/spec exist; /collections is PlaceholderPage (FH-001 P1) | implement complete queue/case/promise/dispute/action/timeline workspace | closes Golden Journey UI gap | remove Collections from MVP/navigation | REQUIRED |
| D03 | Auditor / Support | read-only intent exists but must be exhaustive | explicit least-privilege read-only permission compositions; all mutations 403 | additional negative tests/navigation gates | broader operational mutation role | REQUIRED |
| D04 | Platform Audit / Operations | routes are placeholders | either implement a defined MVP workflow or remove/defer navigation honestly | avoids false-complete product surfaces | leave placeholders | REQUIRED |
| D05 | Customer 360 | customer is central cross-domain anchor; exact required sections need finalization | expose authoritative linked views for contacts/segments/contracts/receivables/collections; avoid duplicated derived truth | more deep-link/browser coverage | keep fragmented navigation | REQUIRED |
| D06 | Receivable operations | allocation/reversal are financial mutations | restrict to explicit manage capability; require idempotency/concurrency/audit semantics | RBAC and audit tests | every human may mutate | REQUIRED |
| D07 | Integration operations | multiple capability permissions and service scopes already exist | keep human manage/read separate from service scopes; diagnostic read least privilege | persona composition + negative tests | broad tenant-admin-only operation | REQUIRED |
| D08 | Campaign/Template/File SoD | explicit capability families exist | retain capability separation and grant only cross-domain reads required by workflow | permission matrix refinement | merge into broad campaign role | REQUIRED |
| D09 | Delivery operations | VC-8 is read-only; no attempt history API | keep monitoring/support read-only; no retry/cancel in current scope | fewer unsafe operator actions; later command contract possible | introduce manual commands now | REQUIRED |
| D10 | Tenant isolation | tenant predicates/security tests exist; platform is separate trust zone | absolute isolation; no implicit platform tenant-data access; future impersonation separate/audited | exhaustive BOLA matrix | platform omniscient business access | REQUIRED |
| D11 | Smoke personas | Alpha/Beta symmetric design documented | personas as permission compositions; no new hard-coded system roles | realistic least-privilege smoke | create many system roles | REQUIRED |
| D12 | Delivery boundary | current hardening plan ends at deterministic adapter/mock | keep real provider outside pre-VC9 gate | deterministic CI | require external provider acceptance now | REQUIRED |
| D13 | Golden Journey | cross-domain path documented but not yet proven end-to-end | make it mandatory release smoke | exposes integration gaps before analytics | rely on independent slice tests | REQUIRED |
| D14 | VC-9 prerequisite | analytics risks hiding unstable upstream behavior | block VC-9 on unwaived P0/P1 + proven Golden Journey/security matrix | delays analytics until foundation is credible | develop analytics in parallel | REQUIRED |

## Decision recording

Replace REQUIRED only with an explicit accepted value, date/context and any scope qualification. If the accepted choice differs from the recommendation, preserve the rationale. Once accepted, derive concrete acceptance criteria and link affected defect/smoke IDs before implementation.
