# Security / Tenant Isolation Matrix

Status: REQUIRED PRE-VC9 GATE

## Security invariants

1. Tenant identity comes from authenticated context, never a client-supplied tenantId.
2. Every tenant-owned lookup/update/delete contains tenantId at the repository/query boundary.
3. Foreign resource IDs in path/query/body do not disclose existence; prefer the established 404/non-disclosure contract where applicable.
4. Authorization is enforced server-side; hidden navigation/buttons are UX only.
5. Human JWT, platform JWT and service JWT are separate trust zones.
6. Capability permissions are least-privilege and re-evaluated after role/status changes.
7. Caches, projections, query keys and async messages include tenant scope wherever cross-tenant collision is possible.
8. Public DTOs/logs exclude secrets, raw credentials, storage internals and unnecessary PII/provider payload.
9. Pagination/input bounds prevent tenant-scoped endpoints becoming amplification primitives.
10. Background workers/recovery/outbox consume tenant identity from durable trusted records/events and cannot cross tenant boundaries.

## Required attack-oriented checks per protected resource

| Vector | Test |
|---|---|
| Horizontal BOLA/IDOR | Tenant A token + Tenant B resource ID in every GET/PUT/PATCH/DELETE/command path |
| Foreign query filter | Tenant A + customerId/assigneeId/campaignId/etc from Tenant B |
| Foreign body reference | Tenant A mutation body referencing Tenant B customer/invoice/template/file/role/source |
| Vertical privilege escalation | restricted TENANT_USER calls read/manage endpoint directly despite hidden UI |
| Service/human confusion | ROLE_SERVICE calls human endpoints; ROLE_HUMAN calls service ingestion endpoints |
| Platform/tenant confusion | tenant principal calls platform APIs; platform principal gets no implicit tenant-business access |
| Stale authorization | remove permission/block user/revoke session, then reuse prior access/refresh token |
| Credential lifecycle | rotate/block service client; old secret/JWT cannot continue |
| Cache isolation | same logical lookup keys in Tenant A/B return only own data; cache key must include tenant |
| Async isolation | queue/outbox/recovery record cannot mutate message/run belonging to another tenant |
| DTO leakage | no tenant-internal storage key/bucket/secret/raw destination/provider body where contract forbids it |
| Log leakage | auth headers, passwords, clientSecret, refresh token, raw destination/provider payload absent |
| Enumeration | foreign ID response does not reveal existence through body/status/timing-sensitive application differences where practical |
| Input amplification | page size, search/filter lengths, file/payload limits are bounded |
| Injection/parser | SQL/filter allow-lists; CSV formula/export risk; XML XXE; template HTML/script policy; filename/header safety |

## Resource isolation coverage target

Identity, roles, sessions, service clients, source schemas, mapping profiles, integration sources, ingestion operations, imports, files, customers, contacts, segments, contracts, invoices, payments, allocations, collection cases/promises/disputes/actions, templates/versions/assets, campaigns/runs/recipients, messages/attachments/attempt projections, documents and existing reporting projections.

For each resource record: READ foreign-id, MUTATE foreign-id, FOREIGN REFERENCE, PERMISSION-DENIED, ANONYMOUS/SERVICE confusion as applicable.

## Severity

P0: demonstrated cross-tenant read/write, credential/secret exposure, auth bypass, financial/delivery cross-tenant corruption.
P1: vertical privilege escalation allowing material business mutation; broken revocation; golden workflow security blocker.
P2: inconsistent non-disclosure, missing negative coverage, over-broad read, unsafe diagnostic/PII exposure without credential compromise.
P3: defense-in-depth/UX security inconsistency.

Do not mark an isolation row PASS from code inspection alone.
