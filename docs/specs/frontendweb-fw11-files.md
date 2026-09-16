# FW11 — Files registry

Status: DRAFT / BACKEND LIST API BLOCKER

Depends on: backend tenant-scoped files registry API

Suggested branches: `feat/files-registry-api`, `feat/frontendweb-fw11-files`

## Цель

Реализовать безопасный tenant files registry: paged list, metadata, upload, download and
delete around existing FileService without exposing RustFS/object-storage details.

## Existing backend contract

- `POST /api/v1/files` multipart;
- `GET /api/v1/files/{fileId}`;
- content/download-url endpoints;
- `DELETE /api/v1/files/{fileId}`;
- authorization bean enforces upload/read/delete;
- metadata has category/project/status/filename/contentType/size/checksum/timestamps.

There is no `GET /api/v1/files` list and repository exposes no tenant-paged registry query.

## Required backend closure

Add fixed-filter endpoint:

```text
GET /api/v1/files
  ?category&status&projectId&filename&createdFrom&createdTo&page&size&sort
```

Response is a screen DTO and MUST NOT expose storage key/bucket/internal URL or arbitrary
tenantId. Query applies tenant predicate in PostgreSQL, bounded size and allow-listed sort.
Indexes must follow measured query shape, initially `(tenant_id, created_at desc, id)` and
selective category/status variants only when justified. Add tenant isolation, paging,
authorization and query-count tests.

## Routes and UI

```text
/files
/files/:fileId
```

- URL-owned fixed filters and server paging;
- metadata side panel/detail route;
- upload validates configured size/type hints but backend remains authoritative;
- download uses `/content` or backend-issued expiring URL only;
- delete requires permission, confirmation and refreshes affected document/import/template
  projections when their reference is known;
- status and retention/expiry are explicit; deleted/expired content is never presented as
  downloadable.

## Security invariants

- never construct RustFS URL or persist presigned URL beyond its short UI lifetime;
- never render object key, bucket, credentials or raw checksum as a public link;
- filename is text, never injected as HTML/content-disposition logic;
- download response errors follow global ProblemDetail handling;
- projectId is a filter within current tenant, not tenant selection.

## Tests

- backend PostgreSQL tenant/paging/filter/sort/index-path tests;
- frontend URL codec/list/empty/error states;
- upload validation and backend-error mapping;
- expiring URL is requested on intent and not cached as durable server state;
- delete permission/confirmation/invalidation;
- malicious filename rendering and foreign-tenant not-found behavior.

## Implementation order

1. Backend list DTO/query/controller/OpenAPI/tests.
2. Frontend file DTO/API/query keys/filter codec.
3. Registry/detail.
4. Upload/download/delete.
5. cross-feature link and security scenarios.

## Не входит

Object-storage browser credentials, folder hierarchy not represented by backend, bulk ZIP,
public sharing and client-side antivirus/content inspection.

## Definition of Done

- tenant can browse files through one bounded paged query;
- storage internals never cross the public UI contract;
- download/delete honor status, expiry and permissions;
- backend/frontend CI is green.
