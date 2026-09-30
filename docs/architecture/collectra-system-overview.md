# Collectra System Overview

Status: CANONICAL — living architecture document  
Last reviewed: 2026-09-30  
Historical evidence origin: `fix/pre-channel-release-gate`  
Current evidence reconciliation: `fix/audit-defects-and-release-gate-alignment`

## 1. Purpose

Collectra is a configurable multi-tenant receivables, collections and communication automation platform.

Its primary business flow is:

```text
External system
 -> Integration Source
 -> Schema / Mapping / Validation
 -> Customer / Contract / Invoice / Payment
 -> Payment Allocation
 -> Collection Case / Action / Promise / Dispute
 -> Campaign / Audience / Eligibility
 -> immutable TemplateVersion
 -> Message / generated document / attachment
 -> Delivery Intent
 -> PostgreSQL Outbox
 -> RabbitMQ
 -> provider-neutral Delivery Worker
 -> external channel
```

Collectra is not only a runtime collections engine. It also contains a configuration plane used to onboard tenants without hard-coding every source format or communication template.

## 2. Architecture planes

### 2.1 Configuration plane

```text
Integration Setup Center
  -> Service Clients
  -> Integration Sources
  -> Source Schemas
  -> Mapping Profiles
  -> readiness / validation

Template Studio
  -> Template
  -> version
  -> builder/raw editor
  -> field catalogue
  -> assets
  -> validation
  -> preview
  -> publish
```

Published/versioned configuration is intended to make imports and communications reproducible.

### 2.2 Business plane

Authoritative business domains:

- tenant and identity/RBAC;
- customer and contact data;
- contracts;
- receivables: invoices, payments and allocations;
- collections: cases, actions, promises and disputes;
- campaigns, audience snapshots and current-state eligibility.

Receivable owns financial truth. Collection owns workflow state and must not independently redefine invoice balance/payment state.

### 2.3 Execution plane

Asynchronous and operational execution includes:

- imports and ingestion diagnostics;
- document generation and file storage;
- message materialization;
- attachment/document readiness gates;
- transactional outbox;
- RabbitMQ work transport;
- provider-neutral delivery worker;
- retry, ambiguous outcome and recovery flows.

PostgreSQL remains the business source of truth. RabbitMQ is an at-least-once work transport.

### 2.4 Analytics plane

Tenant financial analytics combines:

- current OLTP snapshot;
- daily tenant financial projections for closed dates;
- bounded raw fallback for dates without a READY projection;
- DAY/WEEK/MONTH presentation.

Analytics must not turn ordinary reporting requests into unbounded OLTP scans or per-day query loops.

## 3. Cross-cutting invariants

Every plane is constrained by:

- tenant isolation;
- RBAC and authenticated tenant context;
- optimistic/concurrent state control;
- idempotency;
- immutable published configuration where required;
- auditability;
- bounded paging/batch processing;
- secret and sensitive-data handling;
- observability;
- PostgreSQL migrations through Liquibase.

## 4. Repository architecture

The backend is a Java 17 / Spring Boot modular monolith. Major modules include:

```text
audit
campaign
collection
communication
contract
customer
dashboard
document
file
identity
importing
integration
invitation
localization
platform
receivable
reporting
support
template
tenant
```

The browser client lives in `frontendweb/` and uses React, TypeScript, Vite, React Router and TanStack Query. Backend and frontend are runtime-decoupled through `/api/v1/**`.

## 5. Studios

### Integration / Mapping Studio

The integration UI and APIs model tenant-controlled source onboarding:

```text
Source -> Schema -> Mapping -> validation/readiness -> ingestion
```

The current frontend contains Integration Source, Source Schema, Mapping Profile and Service Client pages. The production audit must verify version/publish semantics, mapping reproducibility, schema evolution, sample validation, diagnostics and tenant isolation.

### Template Studio

Template Studio is the tenant authoring environment for communication content and generated documents. Its architecture includes versioned templates, builder/raw editing, field catalogue, tenant assets, validation, preview and lifecycle commands.

Published versions must be treated as immutable execution inputs. Campaign/message execution must not silently switch to a later edited draft.

## 6. Delivery reliability model

```text
business transaction
 -> durable Message / Delivery Intent
 -> durable OutboxEvent
 -> commit
 -> OutboxPublisher
 -> RabbitMQ
 -> MessageDeliveryWorker
 -> DeliveryGateway
```

Provider outcomes distinguish accepted, rejected and unknown/ambiguous results. Recovery must not blindly resend after an uncertain provider acceptance.

## 7. Production-readiness model

Feature existence is not production evidence.

The current fail-closed release contract is `docs/qa/pre-channel-release-gate.md`. An invariant becomes VERIFIED only when its executable evidence is green on the same exact release-candidate SHA.

Production readiness additionally requires deployment/operations evidence outside pure application functionality: secrets, TLS, probes, resource sizing, backup/restore, disaster recovery, broker/storage availability, logging/metrics/alerts, rollback, load/soak testing and real-provider certification where applicable.

## 8. Canonical documentation

Use these documents in this order:

1. this overview for current system architecture;
2. `docs/architecture/technical-audit-ledger.md` for current audit/readiness state;
3. `docs/qa/pre-channel-release-gate.md` for executable pre-provider release evidence;
4. current implementation-ready specs for detailed subsystem contracts;
5. historical slice/FW documents only as historical implementation context.

When historical documentation conflicts with current code or a newer canonical contract, current code plus the newest explicitly canonical contract wins and the conflict must be recorded in the audit ledger.
