# React Template Studio — readiness audit

Date: 2026-09-24  
Target baseline: `main@c576abbc63c6284ddb96fb40528d0412f5044ab9`  
Target implementation slice: T2 / React Template Studio

## 1. Executive conclusion

The previous Template Studio specification is directionally correct, but it is **not implementation-ready against the current project**.

The backend is significantly more mature than the old document assumes:

- direct template detail exists;
- paged template and version registries exist;
- builder JSON schema v1.0 exists;
- EMAIL, PDF and text-channel validation rules exist;
- optimistic mutation service and stable `VERSION_CONFLICT` exist;
- bounded field and asset catalogue endpoints exist;
- builder JSON/content/preview limits exist;
- production PDF binary preview exists;
- PDF/rendering assurance tests exist;
- template entity contracts already exist in `frontendweb` for campaign selection.

However, the old T2 contract still leaves several implementation ambiguities:

1. mutable template/version endpoints still support a legacy no-revision path;
2. `/template-builder/catalog` still embeds unbounded fields/assets;
3. `GET /template-builder/assets` is still an unbounded legacy list;
4. the bounded field DTO omits `description`, `exampleValue`, and `validationRules` although the domain contains them;
5. the old T2 document does not freeze the exact builder JSON v1.0 block grammar;
6. the old document does not distinguish management API, builder API and campaign-selection API precisely enough;
7. it does not define query-key invalidation, editor state transitions, preview race semantics, or stale-write recovery in enough detail;
8. it does not define whether a logical template can have multiple locale/channel variants sharing the same business version number;
9. it does not define exactly what happens when a published version needs further editing;
10. it does not define asset upload/register/archive as a two-step FileService + template-asset flow;
11. it does not define exact UI behaviour for backend validation errors by `code/path/message`;
12. it does not explicitly forbid the frontend from using legacy unbounded endpoints;
13. it does not define the hand-off contract to the existing Campaign UI.

Therefore the recommended next step is:

```text
T2A backend contract closure
        ↓
T2B registry + logical-template detail
        ↓
T2C version editor + builder
        ↓
T2D preview/assets/lifecycle hardening
        ↓
T2E campaign integration + full acceptance
```

The implementation-ready specification is in:

`docs/specs/template-studio-t2-implementation-ready.md`

---

## 2. Current project state

### 2.1 Frontend

Current `main` still routes:

```text
/templates -> PlaceholderPage
```

The frontend already contains template entity support used by Campaign UI:

```text
frontendweb/src/entities/template/model/template.types.ts
frontendweb/src/entities/template/api/template.api.ts
frontendweb/src/entities/template/api/template.queries.ts
```

Those contracts currently cover only:

- template options;
- published version options;
- campaign selection.

They are **not** sufficient for Template Studio management/editor flows.

### 2.2 Management API

Current management endpoints:

```text
GET    /api/v1/templates
POST   /api/v1/templates
GET    /api/v1/templates/{id}
PUT    /api/v1/templates/{id}
DELETE /api/v1/templates/{id}

GET    /api/v1/templates/{id}/versions
POST   /api/v1/templates/{id}/versions
PUT    /api/v1/templates/versions/{id}
POST   /api/v1/templates/versions/{id}/validate
POST   /api/v1/templates/versions/{id}/preview
POST   /api/v1/templates/versions/{id}/{publish|reopen|archive}
```

Positive findings:

- list is server-paged;
- filters include search/channel/status/locale/date;
- direct detail is tenant-scoped;
- logical-template revision exists;
- version list exposes business `templateVersion`;
- lifecycle statuses are explicit.

Problem:

Several mutation endpoints accept `revision == null` and fall back to the legacy non-optimistic service path.

That is acceptable as temporary backward compatibility in backend code, but **not acceptable as the canonical React contract**.

### 2.3 Builder API

Current builder endpoints:

```text
GET  /api/v1/template-builder/catalog
GET  /api/v1/template-builder/catalog/fields?page=&size=
GET  /api/v1/template-builder/catalog/assets?page=&size=

POST /api/v1/template-builder/validate
POST /api/v1/template-builder/preview

POST /api/v1/template-builder/documents/validate
POST /api/v1/template-builder/documents/preview
POST /api/v1/template-builder/documents/preview-pdf

POST /api/v1/template-builder/templates/{templateId}/versions
POST /api/v1/template-builder/templates/{templateId}/versions/builder

PUT  /api/v1/template-builder/versions/{versionId}
PUT  /api/v1/template-builder/versions/{versionId}/builder
GET  /api/v1/template-builder/versions/{versionId}

POST /api/v1/template-builder/versions/{versionId}/validate
POST /api/v1/template-builder/versions/{versionId}/publish

GET/POST/DELETE /api/v1/template-builder/assets...
```

Positive findings:

- real PDF preview exists;
- no frontend-side PDF authority is required;
- paged field catalogue exists;
- paged asset catalogue exists;
- builder version detail exists;
- builder save path exists;
- revision metadata exists;
- actual builder schema has a fixed version.

### 2.4 Builder schema v1.0

Current compiler supports:

HTML/PDF capable blocks:

```text
header
footer
row
column
richText
itemsTable
image
spacer
```

Text-channel compatible blocks:

```text
header
footer
richText
spacer
```

Text channels explicitly reject:

```text
row
column
image
itemsTable
```

Supported richText content nodes:

```json
{"type":"text","value":"..."}
{"type":"placeholder","key":"customer.name"}
```

Supported collection model:

```text
{{#each items}}
  {{item.<field>}}
{{/each}}
```

Nested `each` is not supported.

`itemsTable` is hard-wired to:

```json
{
  "type": "itemsTable",
  "props": {
    "dataSource": "items",
    "columns": [
      {"key":"name","label":"Name"}
    ]
  }
}
```

`image` is asset-key based, not arbitrary URL based.

### 2.5 Channel model

Current enum:

```text
EMAIL
SMS
WHATSAPP
TELEGRAM
PDF
```

Important invariants already enforced:

- EMAIL requires subject;
- text channels do not support stylesheet;
- text channels reject HTML markup;
- PDF is not a delivery campaign channel;
- campaign template version must be PUBLISHED;
- campaign selected version channel must match campaign channel.

### 2.6 Lifecycle model

Current version lifecycle:

```text
DRAFT
  ↓ validate
VALIDATED
  ↓ publish
PUBLISHED
  ↓ archive
ARCHIVED

VALIDATED
  ↓ reopen
DRAFT
```

Current domain explicitly forbids:

- editing non-DRAFT versions;
- publishing non-VALIDATED versions;
- reopening anything except VALIDATED;
- archiving anything except PUBLISHED.

This lifecycle must be rendered literally in UI. The frontend must not invent transitions.

### 2.7 Assets

Actual flow is two-stage:

```text
POST /api/v1/files?category=ASSET
        ↓ returns fileId
POST /api/v1/template-builder/assets
        ↓ key + fileId + altText
builder inserts:
{{asset.<key>}}
```

Current constraints:

- tenant-scoped file;
- category must be ASSET;
- file must be READY;
- allowed MIME: PNG / JPEG / GIF;
- inline asset <= 2 MB;
- remote URL is forbidden by rendering policy.

The old T2 document did not define this flow precisely enough.

### 2.8 Fields / placeholders

The domain contains:

```text
key
label
dataType
category
collection
required
description
exampleValue
validationRules
system
```

But current builder `FieldResponse` exposes only:

```text
id
key
label
dataType
category
collection
required
system
```

Therefore the old requirement "show description/example/type" cannot be implemented fully without backend extension.

### 2.9 Limits

Current hard limits:

```text
content                  256 KB
stylesheet               128 KB
builder JSON             512 KB
preview payload          512 KB
JSON depth               24
JSON nodes               5,000
rendered preview output  2 MB
PDF preview              10 MB
asset inline             2 MB
```

These limits are already sufficient to design frontend pre-checks and error messages.

The old specification called them configuration-backed. The current implementation uses constants. This is not a T2 UI blocker, but the frontend must treat backend as authority.

### 2.10 Rendering assurance

Current test suite already covers core semantic rendering guarantees including:

- placeholder parsing;
- fail-closed malformed templates;
- HTML escaping;
- item collection syntax;
- remote-resource rejection;
- managed assets;
- PDF binary preview contract.

The React editor therefore must not reproduce renderer semantics client-side.

---

## 3. Gaps that must be closed before full T2 implementation

### BLOCKER T2A-01 — mandatory revision contract

Problem:

Backend currently permits mutation without revision on several endpoints.

Required target:

React must always send revision.

For T2 production contract, remove or deprecate frontend-visible legacy path and require revision for:

- logical-template rename;
- logical-template archive;
- raw version update;
- builder version update;
- saved version validate;
- publish;
- reopen;
- archive.

Expected conflict:

```text
HTTP 409
code = VERSION_CONFLICT
```

### BLOCKER T2A-02 — builder capabilities without unbounded libraries

Problem:

`GET /template-builder/catalog` includes fields and assets.

Required target:

Add a metadata-only capability contract, for example:

```text
GET /api/v1/template-builder/capabilities
```

Minimum response:

```json
{
  "builderSchemaVersion": "1.0",
  "channels": ["EMAIL","SMS","WHATSAPP","TELEGRAM","PDF"],
  "eachSyntax": "{{#each items}}...{{/each}}",
  "assetSyntax": "{{asset.<key>}}"
}
```

Frontend must use:

- capabilities endpoint for static builder metadata;
- paged field endpoint for fields;
- paged asset endpoint for assets.

Frontend must never use unbounded field/asset lists.

### BLOCKER T2A-03 — field browser metadata

Extend bounded field response with:

```text
description
exampleValue
validationRules
```

This avoids inventing helper text client-side.

### T2A-04 — stable validation error mapping

Builder validation already returns:

```text
code
path
message
```

T2 must freeze this as the frontend diagnostic contract.

No UI code may parse human-readable English messages to determine behaviour.

### T2A-05 — legacy response field ambiguity

Builder/management version responses currently expose both:

```text
version
templateVersion
revision
```

and `version` is currently equal to business templateVersion.

For React canonical model:

```text
templateVersion = business ordinal
revision        = optimistic concurrency token
```

The frontend must ignore legacy `version`.

A backend cleanup may remove it in a later compatibility window.

---

## 4. Non-blocking improvements

### 4.1 Search on paged field/assets endpoints

Current bounded endpoints support page/size but no search parameter.

For tenants with a large catalogue, add optional:

```text
search=
```

This is recommended but not a hard blocker if UI provides client filtering only within a deliberately bounded loaded page set.

Preferred implementation: server search.

### 4.2 Capabilities can expose block matrix

For a robust schema-driven editor, capabilities may additionally return:

```json
{
  "blocks": {
    "EMAIL": ["header","footer","row","column","richText","itemsTable","image","spacer"],
    "PDF": ["header","footer","row","column","richText","itemsTable","image","spacer"],
    "SMS": ["header","footer","richText","spacer"],
    "WHATSAPP": ["header","footer","richText","spacer"],
    "TELEGRAM": ["header","footer","richText","spacer"]
  }
}
```

Until that exists, frontend may use a local compatibility matrix only if it exactly mirrors schema version `1.0` and is guarded by `builderSchemaVersion`.

### 4.3 Asset upload progress

FileService supports multipart upload but no resumable upload contract.

For T2, simple bounded image upload is sufficient.

---

## 5. Risks explicitly removed from T2

The following are **not** part of T2 and must not appear as hidden assumptions:

- arbitrary HTML/CSS execution;
- provider-specific WhatsApp approved-template management;
- provider send/test-send;
- collaborative editing;
- autosave across multiple browser sessions;
- arbitrary nested layout language;
- arbitrary remote images;
- arbitrary PDF CSS support;
- nested collection loops;
- client-side PDF generation;
- drag/drop free-form page coordinates;
- automatic builder schema migration;
- editing PUBLISHED content in place;
- cross-tenant asset reuse.

---

## 6. Readiness decision

### Backend readiness

Current backend is approximately ready for T2, but T2A closure is required for a clean production contract.

### Frontend readiness

React infrastructure is ready:

- Vite;
- React Router;
- TanStack Query;
- permission gates;
- ProblemDetail handling;
- RU/KK i18n;
- shared dialogs/tables/pagination;
- Campaign UI already consumes published template options.

### Specification readiness before this audit

Not ready for direct implementation.

### Specification readiness after new document

Ready for implementation **after T2A blockers are accepted as part of the T2 slice**.

The implementation must follow `template-studio-t2-implementation-ready.md`.
