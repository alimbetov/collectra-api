# Architecture / Business Decision Register

Status: ACCEPTED BASELINE FOR PRE-VC9 HARDENING
Authority: product/architecture decisions delegated by the product owner for this hardening scope.
Rule: Codex implements these decisions. Re-open only if repository evidence proves a material conflict with an already shipped external contract; record BLOCKED instead of silently changing semantics.

| ID | Accepted decision | Required consequence |
|---|---|---|
| D01 | Business-core RBAC is capability-based; ROLE_HUMAN alone is insufficient for Customer/Contract/Receivable/Collection authorization. | Add/reuse READ/MANAGE capabilities, seeds/migrations, backend guards, frontend gates and persona tests. |
| D02 | Collections is MVP and must be a real workspace. | Implement queue, detail, promise/dispute/action/timeline, lifecycle, paging/filter/deep-link/409/permission UX. |
| D03 | Auditor and Support/Ops are read-only compositions; Support gets safe delivery diagnostics only. | Mutations return 403/no state change; navigation/actions match server capabilities. |
| D04 | Platform Audit and Platform Operations are outside current MVP; placeholders must not look completed. Platform Analytics stays VC-9. | Remove/hide navigation or expose explicitly unavailable/deferred behavior with tests. |
| D05 | Customer detail is the Customer 360 operational anchor. | Link authoritative contacts/segments/contracts/receivables/collections/campaign context where supported; no browser-derived financial truth or N+1 fan-out. |
| D06 | Allocation/reversal are privileged financial mutations. | Explicit receivable-manage authority, idempotency, concurrency, reversal reason/audit and exact balance invariants. |
| D07 | Human integration management and service ingestion are separate trust zones. | Human READ/MANAGE authorities; service ROLE_SERVICE+scopes; stale rotated/blocked credentials rejected. |
| D08 | Campaign/Template/File remain separate capability families. | Cross-domain workflows grant only required reads; no manage/publish/delete privilege leakage. |
| D09 | VC-8 delivery monitoring is read-only; no manual retry/cancel in this hardening. | Safe state/attempt/provider/error projection; no fabricated attempt history or mutation controls. |
| D10 | Tenant isolation is absolute; platform admin has no implicit tenant-business access. | Tenant-scope all path/query/body references; impersonation is separate/audited/out of scope. |
| D11 | Personas are permission compositions, not new system roles. | Keep existing system trust roles; build smoke personas from authorities. |
| D12 | External delivery acceptance stops at deterministic adapter boundary. | No live external provider dependency in CI. |
| D13 | Full Golden Journey is mandatory release evidence. | Composite clean-environment journey plus focused boundary suites. |
| D14 | VC-9 is blocked until fail-closed pre-VC9 gate passes. | No analytics implementation while mandatory blockers remain. |
| D15 | Ambiguous provider acceptance must not cause blind resend. | Stable delivery/idempotency identity and explicit safe handling of accept-then-timeout/unknown acceptance; unsafe current behavior is a P1 defect. |

## Change control
Technical implementation choices do not reopen these decisions. A semantic exception requires a new D16+ entry with evidence and product-owner acceptance; Codex cannot self-approve it.
