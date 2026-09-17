# Template & Document Rendering Assurance

## 1. Purpose

Establish a deterministic quality gate for Collectra template rendering and document generation before FW9 Template Builder is treated as production-ready.

The assurance slice proves the chain:

`normalized payload -> placeholder compilation -> HTML rendering -> locale/font application -> PDF rendering -> semantic verification`

It does not move business rules into the frontend and does not replace the existing template/document architecture.

## 2. Existing architecture retained

The slice builds on the existing components:

- `FieldCatalogService` / `FieldKeyValidator` / `FieldPath`
- `PlaceholderGrammar` / `PlaceholderScanner`
- `HtmlTemplatePolicy`
- `TemplateCompiler`
- `TemplateRenderer`
- `GenerationJobService`
- `PdfRenderer`
- `SupportedLocaleService`
- `FontProfileRegistry`
- OpenHTMLToPDF / PDFBox

## 3. Mandatory invariants

### Placeholder grammar

- malformed or unclosed expressions fail closed;
- nested placeholder expressions fail closed;
- only `{{#each items}} ... {{/each}}` collection blocks are supported;
- nested `each` blocks are rejected;
- item fields are resolved only inside an item block;
- missing/null required values fail rendering;
- inserted HTML values are escaped;
- successful rendering leaves no unresolved template expressions originating from the template.

### HTML/CSS safety

- remote image URLs are forbidden;
- managed `{{asset.<key>}}` references are accepted;
- controlled inline image data is accepted;
- CSS `url(...)` and `@import` are rejected;
- template sanitization remains deterministic.

### PDF semantics

For enabled production locales, at minimum `en`, `ru`, `kk`, and `zh-CN`:

- renderer returns a valid PDF document;
- PDF can be reopened by PDFBox;
- expected Unicode text can be extracted;
- selected fonts are embedded;
- long tabular documents span multiple pages rather than truncating content;
- bundled font resources are verified before rendering.

### Generation reproducibility

- only published template versions are generation inputs;
- mapping document type and template document type must match;
- generation keeps the normalized payload and template SHA-256;
- generated output is tenant-scoped.

## 4. Test layers

### Layer A — grammar/rendering unit assurance

Covers valid placeholders, collection blocks, escaping, missing fields, malformed grammar, unknown values, and HTML/CSS safety policy.

### Layer B — PDF semantic/structural integration assurance

Uses the real Spring context, PostgreSQL migrations, supported locale catalog, bundled fonts, OpenHTMLToPDF and PDFBox. It verifies PDF signature, parseability, extracted text, page count and font embedding.

### Layer C — end-to-end document generation

Existing generation integration tests remain the owner of persistence/outbox/storage workflow. This slice must not duplicate those tests unless a rendering-specific gap is found.

### Layer D — visual golden regression (follow-up)

A separate follow-up PR will rasterize canonical PDF fixtures into PNG images and compare them against reviewed baselines with a small tolerance. Baselines must be produced by a validated renderer run and reviewed visually before they are committed.

Canonical fixtures:

- invoice-en
- invoice-ru
- invoice-kk
- invoice-zh-CN
- long-customer-name
- 100-row-table
- multi-page-invoice
- large-money-values
- missing-optional-fields (when optional-placeholder semantics are introduced)
- logo/managed-asset document

Byte-for-byte PDF snapshots are explicitly forbidden because PDF metadata and font subsetting can change without a visual or semantic regression.

## 5. CI gate

This slice is complete only when normal `mvn clean verify` passes with the new tests. No separate opt-in profile is allowed for the semantic/structural checks.

The later visual-golden job may be isolated if rasterization tooling adds material CI cost, but it must still run as a required PR check before FW9 is considered production-ready.

## 6. Definition of Done

- placeholder grammar matrix automated;
- unresolved/missing values fail closed;
- HTML escaping automated;
- external-resource policy automated;
- font bundle verification automated;
- EN/RU/KK/ZH-CN PDF semantic tests automated;
- PDF parseability and embedded-font assertions automated;
- 100-row/multi-page rendering automated;
- normal Maven CI green;
- visual golden follow-up explicitly tracked before FW9 production readiness.
