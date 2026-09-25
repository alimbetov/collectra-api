# Collectra Integration Journey — reference user story

> Status: reference architecture / product navigation map
>
> Audience: tenant administrator, integration engineer, support, product owner, frontend/backend developers.
>
> This page is the canonical entry point for the external-data-to-communication user story. Detailed implementation requirements are in [Integration Ingestion Gap Specification](./integration-ingestion-gap-spec.md).

## User story

As a Collectra tenant administrator, I want to connect an external business system, define how its fields map into Collectra, securely submit JSON/files through an authenticated integration, reuse company assets and files referenced by source data, and launch document generation/communications from the normalized data, so that the tenant can automate personalized omnichannel communication without changing the source-system data model.

## End-to-end map

| Step | User goal | Collectra capability | Current state | Navigation / next action |
|---|---|---|---|---|
| 1. Tenant | Register and configure company | tenant / identity | Existing | Create tenant users and permissions |
| 2. Integration source | Describe ERP/CRM/ABS that sends data | `IntegrationSource` | **Gap** | Create source, bind credential and active configuration |
| 3. Credentials | Give the source machine access | `ServiceClient`, credential rotation, scopes | Existing | Create/rotate client secret and request service token |
| 4. Request context | Pass request metadata in headers | `IngestionContext` | **Gap** | Configure accepted source headers and mappings |
| 5. Source schema | Describe incoming structure | `SourceSchema`, `SourceField` | Existing | Version, validate and publish schema |
| 6. Mapping | Map source fields to canonical fields | `MappingProfile`, `MappingRule` | Existing | Test rules, preview result, publish mapping |
| 7. Ingestion | Submit JSON/XML/file safely | `ImportBatch` | Partial | Unified source-oriented endpoint and raw archival required |
| 8. Normalize | Produce stable canonical document | `MappingExecutionService` | Existing | Freeze mapping version/hash in batch provenance |
| 9. Persist | Create/reuse business records | Customer / Invoice / Payment | Partial | Connect business persistence to unified orchestration |
| 10. Resources | Resolve URL references and files | FileService / RustFS | **Gap** | Add secure RemoteResourceResolver |
| 11. Tenant assets | Maintain logo/header/signature assets | `TemplateAsset` + FileService | Existing | Register ASSET file under stable asset key |
| 12. Render | Render personalized content/documents | Template + GenerationJob | Existing | Use normalized payload + assets |
| 13. Communicate | Create outbound communication | Campaign / Message | Partial integration | Route normalized result to explicit communication action |
| 14. Deliver | Send and recover | Outbox / Rabbit / provider adapters | Existing foundation | Provider-specific production enablement |
| 15. Observe | Explain ingestion and delivery result | reporting + audit | Partial | Add unified batch/row diagnostics and provenance |

## Golden path

```text
Tenant admin
   |
   +-- creates IntegrationSource "core-banking-prod"
   |      |
   |      +-- binds ServiceClient
   |      +-- selects published SourceSchema
   |      +-- selects published MappingProfile
   |      +-- selects processing/routing policy
   |
External ERP
   |
   +-- obtains service token
   |
   +-- POST /api/v1/integration/sources/core-banking-prod/ingestions
          Authorization: Bearer <service-token>
          Idempotency-Key: ERP-20260925-00001452
          X-Correlation-Id: ...
          X-Source-Event-Id: EVT-91822
          X-Business-Date: 2026-09-25
          X-Branch-Code: 015
          Content-Type: application/json

          { source-specific payload }
                    |
                    v
              IngestionContext
                    |
              Raw source archive
                    |
              MappingProfile vN
                    |
              NormalizedDocument
             /        |         \
            v         v          v
      business data  resources  render
            |         |          |
            |      FileService   |
            |         |          |
            +---------+----------+
                      |
               explicit routing
                      |
                 Message/Outbox
                      |
                    Rabbit
                      |
                  Provider
                      |
                  Reporting
```

## Header contract

Headers have two classes.

**Platform headers** have fixed semantics and are owned by Collectra: `Authorization`, `Idempotency-Key`, `Content-Type`, `X-Correlation-Id`, and optionally `traceparent`. They must not be redefined by tenant mapping.

**Source headers** vary by integration. Examples are `X-Source-Event-Id`, `X-Business-Date`, `X-Branch-Code`, `X-Document-Type`. An `IntegrationSource` may declare an allowlisted mapping from an external header name to a canonical context key. Business code consumes the normalized `IngestionContext`, not arbitrary servlet headers.

Example:

```text
X-Branch-Code      -> context.branchCode
X-Business-Date    -> context.businessDate
X-Source-Event-Id  -> context.sourceEventId
```

Header names and values are validated and size-limited. Authorization/cookies/secrets are never copied to business payload, persisted as generic metadata, or logged. Header mapping cannot override `tenantId`, authenticated principal, source identity, idempotency key, or other trusted server-derived fields.

## Product navigation concept

The frontend should expose **Integrations** as a guided workspace rather than independent technical screens:

```text
Integrations
  -> Sources
      -> Overview
      -> Credentials
      -> Request & headers
      -> Source schema
      -> Field mapping
      -> Test bench
      -> Processing
      -> Assets & remote resources
      -> Activity / ingestion batches
      -> Delivery / analytics
```

The source overview should display a progress map with every step linked to its configuration screen. A user should always be able to answer: **where am I, what is configured, what is blocking activation, and what happens next?**

## Activation readiness

An IntegrationSource cannot become ACTIVE until its service client is active, source schema and mapping are PUBLISHED, the mapping validates, processing mode is selected, required header mappings are valid, and referenced template/routing configuration is usable. Remote URL import is disabled unless an explicit resource policy enables it.

## Existing capabilities to reuse

Do not duplicate `ServiceClient`, `SourceSchema`, `MappingProfile`, `MappingExecutionService`, `BusinessRecordPersistenceService`, `ImportBatch`, `FileService`, `TemplateAsset`, template rendering, GenerationJob, Message/Outbox, or reporting. New work is an orchestration and integration layer around these capabilities.

## Delivery increments

See the linked specification for implementation details. Recommended sequence is I1 Source & Context, I2 Unified Orchestration, I3 Raw Archive & Diagnostics, I4 Remote Resources, I5 Async Workers, I6 Routing/Communication Integration, and I7 Frontend Guided Journey.
