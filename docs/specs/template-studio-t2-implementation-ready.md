# T2 — React Template Studio — implementation-ready specification

Status: READY FOR IMPLEMENTATION AFTER T2A CONTRACT CLOSURE  
Baseline: `main@c576abbc63c6284ddb96fb40528d0412f5044ab9`  
Supersedes for implementation purposes:

- historical `frontendweb-fw09-templates.md`;
- historical `self-service-final-02-template-studio.md`.

Supporting audit:

- `template-studio-t2-readiness-audit-2026-09-24.md`.

---

# 1. Goal

Replace the current `/templates` placeholder with a production-quality tenant-facing Template Studio.

The completed flow must allow a permitted tenant user to:

```text
open template registry
  ↓
create logical template
  ↓
create channel/locale draft
  ↓
author content
  ↓
insert canonical variables/assets
  ↓
validate
  ↓
preview
  ↓
save with optimistic revision
  ↓
publish
  ↓
select the PUBLISHED version in Campaign UI
```

The Studio is an authoring/management client over existing backend authority.

Frontend must not become an alternative template compiler, renderer, security policy engine, PDF engine or lifecycle authority.

---

# 2. Scope

## 2.1 In scope

### Logical templates

- paged registry;
- filters/search/sort;
- create;
- direct detail;
- rename;
- archive;
- version list.

### Template versions

- create channel/locale draft;
- raw text/HTML editor where applicable;
- builder editor for EMAIL/PDF;
- builder editor for text channels only for supported text blocks;
- save;
- validation;
- HTML/text preview;
- authoritative PDF preview;
- lifecycle:
  - DRAFT;
  - VALIDATED;
  - PUBLISHED;
  - ARCHIVED;
  - reopen VALIDATED -> DRAFT.

### Variables

- bounded catalogue;
- canonical placeholder insertion;
- collection syntax;
- system/custom distinction;
- metadata display.

### Assets

- upload ASSET file;
- register template asset key;
- paged/selectable asset library;
- insert asset placeholder;
- archive template asset.

### Concurrency

- mandatory revision on every mutable existing resource;
- stable stale-write handling;
- local draft preservation.

### Permissions

- TEMPLATE_READ;
- TEMPLATE_MANAGE;
- TEMPLATE_PUBLISH;
- FIELD_READ;
- FileService permissions required by asset upload.

### Campaign hand-off

- after publish, campaign template option/version query caches are invalidated;
- Campaign UI sees newly published compatible versions without reload.

---

## 2.2 Explicitly out of scope

- provider send/test-send;
- provider-specific WhatsApp approved templates;
- external template marketplace;
- collaborative editing;
- autosave as canonical persistence;
- arbitrary executable scripting;
- arbitrary remote resources;
- arbitrary HTML JavaScript;
- arbitrary CSS URL/import;
- client-side authoritative PDF generation;
- free-form coordinate-based page designer;
- nested `each`;
- arbitrary data-source loops;
- schema migration between incompatible builder versions;
- editing PUBLISHED content in place;
- direct object-storage URL construction;
- cross-tenant assets;
- campaign business logic changes.

---

# 3. Architectural rules

Use existing frontend layering:

```text
app
pages
features
entities
shared
```

Recommended additions:

```text
frontendweb/src/entities/template/
  api/
  model/

frontendweb/src/features/templates/
  registry/
  logical-template/
  version-editor/
  variable-browser/
  asset-browser/
  preview/
  lifecycle/

frontendweb/src/pages/templates/
  TemplatesPage.tsx
  TemplateCreatePage.tsx
  TemplateDetailPage.tsx
  TemplateVersionEditorPage.tsx
  TemplateAssetsPage.tsx
```

Mandatory:

- TanStack Query owns remote server state;
- local reducer/form state owns unsaved editor state;
- React Router owns navigable route state;
- existing `apiRequest` owns HTTP;
- existing ProblemDetail model owns API errors;
- existing i18n owns labels;
- existing auth context owns permissions.

Forbidden:

- Redux/MobX/Zustand solely for this feature;
- duplicate fetch client;
- direct `fetch` scattered in components;
- storing unsaved editor draft as authoritative query cache;
- inferring backend lifecycle from button history.

---

# 4. Routes

Target routes:

```text
/templates
/templates/new
/templates/:templateId
/templates/:templateId/versions/:versionId
/templates/:templateId/versions/:versionId/builder
/templates/assets
```

Canonical behaviour:

- `/templates/:templateId/versions/:versionId`
  - channel-aware editor shell;
  - text/raw editor may remain here.

- `/templates/:templateId/versions/:versionId/builder`
  - builder-first editor;
  - only valid for versions whose authoring mode is builder-managed.

If implementation chooses one editor route internally, both URLs may resolve to the same editor component, but deep links must remain stable.

---

# 5. Permissions matrix

| Capability | Permission |
|---|---|
| list/read template | TEMPLATE_READ |
| read version | TEMPLATE_READ |
| read field catalogue | TEMPLATE_READ + FIELD_READ |
| read assets | TEMPLATE_READ |
| create template | TEMPLATE_MANAGE |
| rename template | TEMPLATE_MANAGE |
| archive logical template | TEMPLATE_MANAGE |
| create version | TEMPLATE_MANAGE |
| edit DRAFT | TEMPLATE_MANAGE |
| validate | TEMPLATE_MANAGE |
| preview | TEMPLATE_MANAGE |
| upload/register/archive asset | TEMPLATE_MANAGE + required FileService permission |
| publish/reopen/archive version | TEMPLATE_PUBLISH |

Frontend hiding a control is UX only.

Backend remains authorization authority.

403 behaviour:

- direct forbidden route -> existing forbidden page;
- forbidden mutation discovered after render -> ProblemDetail panel/toast;
- never translate 403 into 404 client-side.

---

# 6. Backend closure — T2A

T2 implementation starts with T2A. No builder UI should be merged before these contracts are stable.

## 6.1 Mandatory revision

Canonical frontend contract requires revision for every mutation of an existing resource.

### Logical template rename

```http
PUT /api/v1/templates/{templateId}
```

Body:

```json
{
  "name": "Payment reminder",
  "revision": 4
}
```

`revision` must be required.

### Logical template archive

```http
DELETE /api/v1/templates/{templateId}?revision=4
```

`revision` must be required.

### Raw draft update

```http
PUT /api/v1/templates/versions/{versionId}
```

or the builder counterpart.

`revision` must be required.

### Builder draft update

```http
PUT /api/v1/template-builder/versions/{versionId}/builder
```

`revision` must be required.

### Validate

```http
POST /api/v1/templates/versions/{versionId}/validate?revision=4
```

or builder saved-version validate.

`revision` must be required.

### Lifecycle

```http
POST /api/v1/templates/versions/{versionId}/publish?revision=4
POST /api/v1/templates/versions/{versionId}/reopen?revision=4
POST /api/v1/templates/versions/{versionId}/archive?revision=4
```

`revision` must be required.

Stable stale-write response:

```text
HTTP 409
ProblemDetail.code = VERSION_CONFLICT
```

Frontend never uses mutation endpoint without revision.

---

## 6.2 Builder capabilities endpoint

Add metadata-only endpoint:

```http
GET /api/v1/template-builder/capabilities
```

Response minimum:

```json
{
  "builderSchemaVersion": "1.0",
  "channels": ["EMAIL", "SMS", "WHATSAPP", "TELEGRAM", "PDF"],
  "eachSyntax": "{{#each items}}...{{/each}}",
  "assetSyntax": "{{asset.<key>}}"
}
```

Preferred additional response:

```json
{
  "blockSupport": {
    "EMAIL": ["header","footer","row","column","richText","itemsTable","image","spacer"],
    "PDF": ["header","footer","row","column","richText","itemsTable","image","spacer"],
    "SMS": ["header","footer","richText","spacer"],
    "WHATSAPP": ["header","footer","richText","spacer"],
    "TELEGRAM": ["header","footer","richText","spacer"]
  }
}
```

React must not depend on unbounded libraries embedded in legacy `/catalog`.

---

## 6.3 Bounded fields

Canonical endpoint:

```http
GET /api/v1/template-builder/catalog/fields?page=0&size=50&search=
```

`search` is preferred and should be added in T2A.

Canonical item DTO:

```json
{
  "id": "uuid",
  "key": "customer.displayName",
  "label": "Customer display name",
  "dataType": "STRING",
  "category": "CUSTOMER",
  "collection": false,
  "required": true,
  "system": true,
  "description": "Human-readable description",
  "exampleValue": "ACME",
  "validationRules": {}
}
```

No frontend-generated descriptions.

---

## 6.4 Bounded assets

Canonical list endpoint:

```http
GET /api/v1/template-builder/catalog/assets?page=0&size=50&search=
```

`search` preferred in T2A.

React must not use:

```text
GET /api/v1/template-builder/assets
```

for bulk listing.

Register/archive remain:

```http
POST   /api/v1/template-builder/assets
DELETE /api/v1/template-builder/assets/{assetId}
```

---

## 6.5 Response naming

Canonical frontend version model:

```text
templateVersion  business ordinal
revision         optimistic concurrency token
```

Legacy `version` from current version response must not be used by Studio.

---

# 7. Canonical API matrix

## 7.1 Registry

```http
GET /api/v1/templates
```

Query:

```text
search
channel
status
locale
createdFrom
createdTo
page
size
sort
```

Frontend defaults:

```text
page=0
size=50
sort=createdAt,desc
```

URL owns:

- search;
- channel;
- status;
- locale;
- createdFrom;
- createdTo;
- page;
- sort.

---

## 7.2 Create logical template

```http
POST /api/v1/templates
```

Body:

```json
{
  "code": "PAYMENT_REMINDER",
  "name": "Payment reminder",
  "documentType": "NOTIFICATION"
}
```

Rules:

- code entered explicitly;
- code normalized by backend;
- duplicate code handled through ProblemDetail;
- do not auto-regenerate code after creation.

---

## 7.3 Direct detail

```http
GET /api/v1/templates/{templateId}
```

Frontend model:

```text
id
code
name
documentType
status
createdAt
updatedAt
revision
```

Direct reload must never depend on scanning template list.

---

## 7.4 Version list

```http
GET /api/v1/templates/{templateId}/versions
```

Query:

```text
channel
status
locale
createdFrom
createdTo
page
size
```

Canonical item:

```text
id
templateId
templateVersion
locale
channel
subject
status
createdAt
updatedAt
revision
```

If current response field is named `version`, API closure should expose/normalize it as `revision` for Studio.

---

## 7.5 Version detail

Canonical:

```http
GET /api/v1/template-builder/versions/{versionId}
```

Use this endpoint for editor reload.

Do not add another duplicate version-detail API.

Canonical frontend DTO:

```text
id
templateId
templateVersion
locale
channel
subject
builderJson
content
stylesheet
status
createdAt
updatedAt
revision
```

---

# 8. Logical template registry UX

## 8.1 Columns

Minimum:

- name;
- code;
- documentType;
- status;
- createdAt;
- updatedAt;
- open action.

Optional summary after bounded backend support:

- locales;
- channels;
- latest published version.

Do not perform N+1 version calls per row.

If summary cannot be returned by list API in one bounded query, omit it in T2B.

---

## 8.2 Filters

- free-text search;
- channel;
- status;
- locale;
- created-from;
- created-to;
- sort.

Filter rules:

- all filters encoded in URL;
- changing filter resets page to zero;
- invalid URL values fall back safely;
- no implicit background query per keystroke unless debounced;
- "Apply" search is acceptable.

---

## 8.3 Empty/loading/error

Required states:

- first load spinner/skeleton;
- refreshing state without clearing current table;
- no templates;
- no results for current filters;
- 403;
- 5xx with retry.

---

# 9. Logical template detail

Header:

- name;
- code;
- document type;
- status;
- revision;
- createdAt;
- updatedAt.

Actions:

- rename;
- archive logical template;
- create variant/version.

Version table:

- business templateVersion;
- locale;
- channel;
- subject for EMAIL;
- lifecycle status;
- updatedAt;
- open.

Version table must be server-paged.

No N+1 detail calls.

---

# 10. Create variant/version

User chooses:

```text
channel
locale
authoring mode
```

Authoring mode:

### EMAIL

Default: builder-managed.

Alternative raw HTML mode is allowed only if product explicitly exposes it.

Recommended T2: builder-managed EMAIL only.

### PDF

Builder-managed.

### SMS / WHATSAPP / TELEGRAM

Text authoring mode.

Do not expose builder blocks unsupported by text channel.

---

# 11. Editor state machine

Editor must keep separate state:

```ts
type TemplateEditorState = {
  serverSnapshot: VersionDto;
  draft: EditorDraft;
  dirty: boolean;
  validation: ValidationResult | null;
  validationRevision: number | null;
  preview: PreviewState;
  conflict: ConflictState | null;
};
```

Definitions:

### serverSnapshot

Last authoritative version returned by backend.

### draft

Current unsaved user input.

### dirty

`draft != editorProjection(serverSnapshot)`.

### validation

Last backend validation result.

Validation is invalidated by any draft edit.

### preview

Current request sequence and result.

### conflict

Set after `VERSION_CONFLICT`.

Never replace `draft` automatically on conflict.

---

# 12. Builder schema v1.0

Root:

```json
{
  "version": "1.0",
  "blocks": []
}
```

## 12.1 richText

```json
{
  "type": "richText",
  "props": {
    "content": [
      {"type":"text","value":"Hello "},
      {"type":"placeholder","key":"customer.displayName"}
    ]
  }
}
```

Frontend stores placeholders structurally as `placeholder` nodes.

Do not persist display labels into canonical key.

---

## 12.2 row

```json
{
  "type": "row",
  "children": []
}
```

Supported only EMAIL/PDF.

---

## 12.3 column

```json
{
  "type": "column",
  "children": []
}
```

Supported only EMAIL/PDF.

---

## 12.4 header/footer

```json
{
  "type": "header",
  "children": []
}
```

```json
{
  "type": "footer",
  "children": []
}
```

Supported all channels.

For text channels they compile to plain text/newline semantics.

---

## 12.5 itemsTable

```json
{
  "type": "itemsTable",
  "props": {
    "dataSource": "items",
    "columns": [
      {"key":"name","label":"Name"},
      {"key":"amount","label":"Amount"}
    ]
  }
}
```

Rules:

- EMAIL/PDF only;
- `dataSource` must equal `items`;
- columns non-empty;
- column key is the item field suffix;
- nested each not exposed.

---

## 12.6 image

```json
{
  "type": "image",
  "props": {
    "assetKey": "logo",
    "alt": "Company logo"
  }
}
```

Rules:

- EMAIL/PDF only;
- asset selected from tenant asset catalogue;
- no arbitrary URL field;
- no base64 manually entered by user.

---

## 12.7 spacer

```json
{
  "type": "spacer",
  "props": {
    "heightPx": 16
  }
}
```

HTML/PDF backend accepts:

```text
0..500 px
```

Frontend enforces same display constraint, backend remains authority.

Text channels render spacer as newline.

---

# 13. Channel UX

## 13.1 EMAIL

Required properties:

- locale;
- subject;
- builder content;
- optional stylesheet.

Subject:

- required;
- placeholder insertion supported;
- plain-text template semantics;
- no HTML.

Body:

- builder v1.0;
- authoritative HTML generated by backend.

Preview:

- backend browser preview;
- sandboxed/sanitized iframe;
- no script execution.

---

## 13.2 PDF

Required:

- locale;
- builder content;
- optional stylesheet.

Preview:

```http
POST /api/v1/template-builder/documents/preview-pdf
Accept: application/pdf
```

Frontend:

- converts response to Blob URL;
- revokes old Blob URL on replacement/unmount;
- never persists preview Blob;
- no-store semantics respected.

PDF preview is authoritative.

HTML preview may be shown as convenience but is not authority for layout.

---

## 13.3 SMS

Plain-text mode.

Display:

- character count;
- placeholder browser;
- exact backend preview.

No:

- stylesheet;
- images;
- row;
- column;
- itemsTable;
- HTML.

Do not enforce SMS provider segment billing rules in T2 unless backend exposes them.

---

## 13.4 WHATSAPP

Same T2 authoring semantics as generic text channel.

Provider-approved template binding is out of scope.

---

## 13.5 TELEGRAM

Same T2 authoring semantics as generic text channel.

Provider-specific Markdown/HTML mode is not introduced unless backend contract explicitly supports it.

---

# 14. Variables browser

Data source:

```http
GET /api/v1/template-builder/catalog/fields
```

Never hardcode business field catalogue in React.

Group by:

- category;
- system/custom;
- collection/non-collection.

Display:

- label;
- canonical key;
- data type;
- required;
- system/custom;
- description;
- example value.

Insertion:

### Scalar

```text
{{customer.displayName}}
```

### Collection

The browser must not insert:

```text
{{items.name}}
```

It must guide user to supported collection construct.

For text/raw mode:

```text
{{#each items}}
{{item.name}}
{{/each}}
```

For visual EMAIL/PDF:

prefer `itemsTable`.

Canonical key is inserted exactly as returned.

No client-side case normalization.

---

# 15. Asset flow

## 15.1 Upload

```http
POST /api/v1/files?category=ASSET
Content-Type: multipart/form-data
```

Accept frontend file picker only for backend-supported types:

- PNG;
- JPEG;
- GIF.

Frontend may pre-check 2 MB, but backend is final authority.

After upload returns `fileId`.

---

## 15.2 Register

```http
POST /api/v1/template-builder/assets
```

Body:

```json
{
  "key": "company_logo",
  "fileId": "uuid",
  "altText": "Company logo"
}
```

Backend returns placeholder.

Frontend uses returned key/placeholder, never constructs storage URL.

---

## 15.3 Archive

```http
DELETE /api/v1/template-builder/assets/{assetId}
```

Archive confirmation required.

After archive invalidate:

- asset list;
- capabilities only if capabilities contain asset-dependent metadata;
- current editor validation.

Existing drafts referencing archived asset may become invalid.

UI must show backend `UNKNOWN_ASSET` diagnostic on next validate/preview.

---

# 16. Save behaviour

## 16.1 Builder-managed draft

Update:

```http
PUT /api/v1/template-builder/versions/{versionId}/builder
```

Body includes:

```json
{
  "channel": "EMAIL",
  "locale": "ru",
  "subject": "Reminder {{customer.displayName}}",
  "builderJson": {
    "version": "1.0",
    "blocks": []
  },
  "stylesheet": "",
  "revision": 7
}
```

On success:

- replace serverSnapshot;
- replace revision;
- keep draft equal to returned version projection;
- set dirty=false;
- clear conflict;
- clear validation because server revision changed unless save response proves same validated state.

---

## 16.2 Text draft

Use raw draft endpoint.

Body:

```json
{
  "channel": "SMS",
  "locale": "ru",
  "subject": null,
  "content": "Hello {{customer.displayName}}",
  "stylesheet": null,
  "revision": 3
}
```

---

# 17. Validation

Two validation modes exist.

## 17.1 Unsaved draft validation

Use document/draft validation endpoint.

Purpose:

- validate current local state before save;
- map errors to editor controls.

This does not transition persisted lifecycle.

## 17.2 Saved-version validation

Use saved version validate with revision.

This transitions:

```text
DRAFT -> VALIDATED
```

Frontend must distinguish these two actions.

Recommended UI labels:

- "Check draft" = unsaved draft validation;
- "Validate version" = persisted lifecycle validation.

Do not call lifecycle validate automatically on every edit.

---

# 18. Validation diagnostics

Backend issue shape:

```text
code
path
message
```

Frontend routing:

| path | Target |
|---|---|
| subject | subject control |
| content | text/raw editor |
| stylesheet | stylesheet control |
| builderJson or block path | builder canvas/property panel |
| unknown/global | diagnostics panel |

Frontend behaviour must use `code` and `path`, not parse `message`.

Examples:

```text
SUBJECT_REQUIRED
CONTENT_REQUIRED
STYLESHEET_NOT_SUPPORTED
HTML_NOT_SUPPORTED
INVALID_TEMPLATE
UNKNOWN_PLACEHOLDER
UNKNOWN_ASSET
ITEM_PLACEHOLDER_OUTSIDE_BLOCK
COLLECTION_PLACEHOLDER_REQUIRES_BLOCK
```

Message is displayed to user/support but is not program logic.

---

# 19. Preview

Preview request must carry explicit current draft + sample payload.

## 19.1 Sequence safety

Maintain monotonically increasing sequence:

```text
previewRequestVersion = 1,2,3...
```

When response arrives:

```text
if response.requestVersion != currentRequestVersion:
    discard
```

Alternatively AbortController may cancel previous request, but sequence guard remains recommended.

Old preview must never replace newer preview.

---

## 19.2 Sample payload

Initial T2:

- editable JSON textarea;
- validate JSON client-side before request;
- backend remains semantic authority;
- preview payload never logged by frontend.

Future structured sample-data UI is out of scope.

Persist sample payload only in local browser/session state if desired; never save into template entity unless backend contract is added.

---

## 19.3 EMAIL preview

Use authoritative backend-rendered HTML.

Render in sandboxed iframe.

Requirements:

- no `allow-scripts`;
- no arbitrary URL resolution;
- Blob/srcDoc lifecycle cleaned up;
- show subject separately.

---

## 19.4 PDF preview

Use binary endpoint.

Requirements:

- loading state;
- error state;
- request race protection;
- Blob URL cleanup;
- actual PDF frame/download-safe browser presentation;
- no assumption that HTML preview equals PDF.

---

## 19.5 Text preview

Display exact backend-rendered text.

Use whitespace-preserving element.

Do not render as HTML.

---

# 20. Lifecycle UI

Exact matrix:

| Status | Editable | Validate | Publish | Reopen | Archive |
|---|---:|---:|---:|---:|---:|
| DRAFT | yes | yes | no | no | no |
| VALIDATED | no | no | yes | yes | no |
| PUBLISHED | no | no | no | no | yes |
| ARCHIVED | no | no | no | no | no |

If product later allows cloning PUBLISHED into new DRAFT, that is a separate command.

T2 must not fake "edit published" by locally switching status.

---

# 21. Conflict handling

On:

```text
HTTP 409
code = VERSION_CONFLICT
```

UI must:

1. preserve current local draft;
2. freeze destructive resubmit until user chooses;
3. fetch authoritative latest version;
4. show:
   - local revision;
   - server revision;
   - server updatedAt;
5. offer:
   - "Reload server version";
   - "Keep my local draft for manual reapply".

T2 does not implement automatic field-level merge.

If user reloads authoritative state:

- replace serverSnapshot;
- replace draft;
- dirty=false.

If user keeps local draft:

- update only serverSnapshot/revision metadata;
- keep draft;
- dirty=true;
- user manually reviews and retries.

Never silently overwrite local draft.

---

# 22. Dirty-navigation protection

Dirty if:

```text
draft differs from saved serverSnapshot projection
```

Protect:

- route change;
- version switch;
- template switch;
- browser refresh/close where browser supports `beforeunload`.

Do not warn if:

- no changes;
- after successful save;
- read-only version.

Pending mutation must also block duplicate actions.

---

# 23. Query keys

Extend existing `templateKeys`.

Recommended:

```ts
templateKeys = {
  all: ['templates'],
  lists: () => ['templates', 'list'],
  list: (params) => ['templates', 'list', params],
  detail: (templateId) => ['templates', 'detail', templateId],
  versions: (templateId) => ['templates', 'versions', templateId],
  versionList: (templateId, params) => ['templates', 'versions', templateId, params],
  version: (versionId) => ['templates', 'version', versionId],
  capabilities: () => ['templates', 'builder-capabilities'],
  fields: () => ['templates', 'fields'],
  fieldPage: (params) => ['templates', 'fields', params],
  assets: () => ['templates', 'assets'],
  assetPage: (params) => ['templates', 'assets', params],
};
```

Existing campaign option query keys remain compatible but should be invalidated after publish/archive.

---

# 24. Invalidation graph

## Create logical template

Invalidate:

```text
template lists
campaign template option lists if they include logical templates
```

## Rename/archive logical template

Invalidate:

```text
template detail
template lists
campaign template option lists
```

## Create/update version

Invalidate:

```text
version detail
version lists for template
template detail if summary later exists
```

## Publish/archive version

Invalidate:

```text
version detail
version lists
template lists/detail
campaign template options
published version options for template/channel
```

## Asset register/archive

Invalidate:

```text
asset pages
current validation/preview result
```

---

# 25. Campaign integration

Existing Campaign UI consumes:

```text
GET /api/v1/templates?channel=...
GET /api/v1/templates/{templateId}/versions?channel=...&status=PUBLISHED
```

T2 must not replace that contract.

After publish:

- invalidate existing campaign template option query;
- invalidate published version option query.

Campaign must continue to receive only PUBLISHED versions.

Channel compatibility remains backend-authoritative.

PDF versions are document versions and are not selectable as delivery campaign channel.

---

# 26. UI structure

Desktop editor target:

```text
┌─────────────────────────────────────────────────────────────┐
│ Template / version header + lifecycle actions              │
├──────────────┬──────────────────────────┬───────────────────┤
│ Blocks /     │ Canvas / text editor     │ Properties /      │
│ Variables /  │                          │ diagnostics       │
│ Assets       │                          │                   │
├──────────────┴──────────────────────────┴───────────────────┤
│ Preview panel / tabs                                       │
└─────────────────────────────────────────────────────────────┘
```

Narrow viewport:

- switch side panels into tabs/drawers;
- editor remains usable;
- no forced three-column horizontal overflow.

---

# 27. Builder interaction model

T2 should prefer deterministic controls over free-form design-tool behaviour.

Allowed interactions:

- add block;
- remove block;
- move block up/down;
- move between compatible containers;
- edit block props;
- insert placeholder;
- select asset;
- reorder via keyboard/button controls.

Optional drag/drop may be added only if keyboard alternative exists.

Free-form x/y placement is out of scope.

---

# 28. Accessibility

Mandatory:

- labels for all inputs;
- keyboard block selection;
- keyboard block reordering alternative;
- focus moves to first validation error;
- error summary links/focuses relevant control;
- dialogs trap focus through existing shared component;
- visible selected block state;
- lifecycle status not conveyed by color only;
- preview iframe has title;
- icon-only buttons have accessible name.

---

# 29. Error handling

Use existing ProblemDetail.

Required mappings:

### 400 / validation

Show request validation problem.

### 403

Permission error.

### 404

Template/version removed or inaccessible.

Offer return to list.

### 409 VERSION_CONFLICT

Conflict flow described above.

### 409 lifecycle/state conflict

Refetch authoritative version and show state-specific error.

### payload too large

Show specific user-facing guidance for:

```text
TEMPLATE_CONTENT_TOO_LARGE
TEMPLATE_STYLESHEET_TOO_LARGE
BUILDER_DOCUMENT_TOO_COMPLEX
PREVIEW_PAYLOAD_TOO_LARGE
PREVIEW_OUTPUT_TOO_LARGE
```

### 5xx

ProblemDetail panel + retry.

Do not discard draft on any transport/server error.

---

# 30. Frontend size pre-checks

Frontend may warn/prevent obviously oversized data using known current limits:

```text
content      256 KB
stylesheet   128 KB
builderJson  512 KB
preview JSON 512 KB
asset        2 MB
```

These checks are UX only.

Backend limits remain authoritative.

Do not duplicate JSON depth/node-count logic in frontend beyond coarse warnings.

---

# 31. Security rules

Frontend must never:

- accept arbitrary remote image URL;
- build object-storage URL;
- render unsanitized arbitrary HTML in main DOM;
- enable iframe scripts;
- log sample payload;
- log rendered customer preview body;
- log access/refresh token;
- expose archived/cross-tenant assets;
- trust client validation as publish authority.

Published versions are read-only.

---

# 32. Testing strategy

## 32.1 Backend T2A

Integration tests:

- revision required on all mutable template/version commands;
- stale revision -> `VERSION_CONFLICT`;
- capabilities is bounded/static;
- field response includes description/exampleValue/validationRules;
- field page tenant isolation;
- asset page tenant isolation;
- legacy unbounded endpoints not used by canonical contract;
- OpenAPI baseline updated.

---

## 32.2 Registry

Vitest/MSW:

- URL filters;
- pagination;
- invalid URL fallback;
- empty/filter-empty/error;
- direct detail reload;
- permission gate;
- create logical template;
- rename conflict.

---

## 32.3 Version list/create

Tests:

- version paging;
- channel/locale filters;
- create EMAIL;
- create PDF;
- create SMS;
- unsupported mode not offered;
- route reload.

---

## 32.4 Builder

Tests:

- schema 1.0 document load;
- unknown schema -> read-only unsupported-schema error, never mutate;
- add/remove/reorder supported blocks;
- unsupported channel block not offered;
- canonical variable insertion;
- itemsTable configuration;
- asset insertion;
- dirty state.

---

## 32.5 Save/conflict

Tests:

- revision sent;
- successful save resets dirty;
- 409 preserves draft;
- authoritative refetch occurs;
- user chooses reload;
- user chooses keep local draft.

---

## 32.6 Preview

Tests:

- EMAIL backend preview;
- text exact preview;
- PDF Blob preview;
- Blob URL revoked;
- stale response ignored;
- preview error preserves editor.

---

## 32.7 Lifecycle

Tests:

- DRAFT action matrix;
- VALIDATED action matrix;
- PUBLISHED read-only;
- ARCHIVED read-only;
- validate transition;
- publish transition;
- reopen transition;
- archive transition;
- duplicate action disabled;
- query invalidation.

---

## 32.8 Assets

Tests:

- upload ASSET;
- >2 MB client warning;
- register key;
- paged list;
- insert;
- archive;
- archived asset validation failure surfaced.

---

## 32.9 i18n

At minimum:

- RU labels;
- KK labels;
- no raw missing message keys in Template Studio routes.

---

# 33. End-to-end acceptance scenarios

## Scenario A — EMAIL

```text
create logical template
create ru/EMAIL draft
add subject placeholder
add richText
add asset
save
validate draft
preview HTML
validate saved version
publish
open campaign
new PUBLISHED EMAIL version is selectable
```

Expected:

- no stale query;
- no full page reload;
- correct channel;
- revision increments;
- PUBLISHED editor becomes read-only.

---

## Scenario B — PDF

```text
create ru/PDF draft
build header + richText + itemsTable + image
save
preview PDF
validate
publish
```

Expected:

- preview starts with valid PDF;
- Blob lifecycle clean;
- no persistent generation artifact;
- unsupported remote resource impossible from UI.

---

## Scenario C — SMS

```text
create SMS draft
enter plain text
insert customer placeholder
attempt HTML unavailable
attempt image unavailable
save
preview exact text
validate
publish
```

---

## Scenario D — stale save

Two tabs load revision 4.

Tab A saves -> revision 5.

Tab B saves revision 4.

Expected:

```text
409 VERSION_CONFLICT
local draft retained
server revision refetched
explicit user decision required
```

No lost edit.

---

## Scenario E — stale preview race

Preview A request starts.

User edits.

Preview B starts.

B returns.

A returns later.

Expected:

- B stays visible;
- A discarded.

---

## Scenario F — archived asset

Draft references `asset.logo`.

Asset archived.

Next validation/preview:

- backend returns UNKNOWN_ASSET;
- UI maps diagnostic;
- local draft remains;
- user can select replacement asset.

---

# 34. Implementation sequence

## T2A — backend contract closure

Branch suggestion:

```text
feat/template-studio-t2a-contract-closure
```

Deliver:

- mandatory revision;
- capabilities endpoint;
- bounded field DTO metadata;
- optional server search fields/assets;
- OpenAPI baseline;
- integration tests.

No React screens.

Gate:

- `mvn verify` green;
- OpenAPI gate green.

---

## T2B — registry + logical template detail

Branch:

```text
feat/template-studio-t2b-registry
```

Deliver:

- template entity DTO expansion;
- list API client;
- query keys;
- `/templates`;
- `/templates/new`;
- `/templates/:templateId`;
- versions table;
- create/rename/archive;
- tests.

Gate:

- typecheck;
- Vitest;
- production build.

---

## T2C — version editor

Branch:

```text
feat/template-studio-t2c-editor
```

Deliver:

- direct version reload;
- local editor state machine;
- EMAIL/PDF builder;
- text editor;
- variable browser;
- save/conflict;
- dirty-navigation.

---

## T2D — preview/assets/lifecycle

Branch:

```text
feat/template-studio-t2d-preview-assets-lifecycle
```

Deliver:

- assets page/browser;
- FileService upload;
- HTML preview;
- PDF preview;
- text preview;
- sequence guard;
- validation diagnostics;
- validate/publish/reopen/archive.

---

## T2E — integration hardening

Branch:

```text
feat/template-studio-t2e-integration-hardening
```

Deliver:

- campaign query invalidation;
- direct reload regression;
- permission matrix;
- RU/KK;
- all acceptance scenarios;
- full backend/frontend CI;
- remove `/templates` placeholder permanently.

---

# 35. Definition of Done

T2 is done only when all statements are true:

- `/templates` is no longer placeholder;
- template registry is server-paged and URL-filtered;
- logical template direct detail works;
- version direct reload works;
- EMAIL authoring works;
- PDF authoring works;
- SMS/WHATSAPP/TELEGRAM text authoring works;
- builder uses schema v1.0 only;
- variables come from bounded backend field API;
- assets use FileService + template asset registration;
- no React path uses unbounded field/asset list;
- every mutable existing resource sends revision;
- 409 preserves local draft;
- preview race is safe;
- actual backend PDF preview is used;
- published versions are read-only;
- lifecycle matches domain exactly;
- campaign sees newly published versions after invalidation;
- RU/KK labels are present;
- permissions are enforced in UI and backend;
- frontend typecheck/tests/build green;
- backend `mvn verify` green;
- OpenAPI compatibility gate green;
- no new security exception for remote resources/unsafe HTML.

---

# 36. Start gate for implementation

Implementation may start when T2A accepts the following decisions without further ambiguity:

1. revision is mandatory for canonical mutable contract;
2. capabilities endpoint is metadata-only;
3. React never uses unbounded catalogue/assets list;
4. bounded field DTO includes helper metadata;
5. `templateVersion` and `revision` are distinct frontend concepts;
6. builder schema v1.0 block list is frozen for T2;
7. PUBLISHED is immutable;
8. provider-specific WhatsApp templates are out of scope;
9. actual PDF preview remains backend authority;
10. Campaign API contract remains unchanged.

Once T2A is merged, T2B implementation can proceed without additional architecture discovery.
