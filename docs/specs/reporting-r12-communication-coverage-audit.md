# R1.2 — Communication Reporting Coverage Audit

## Purpose

This audit verifies that Collectra reporting covers the complete outbound communication flow currently represented by the domain model.

There is no separate direct-notification aggregate in the current backend. Every outbound business communication is represented through:

```text
Campaign
  -> CampaignRun
  -> CampaignRecipient
  -> Message
  -> MessageDeliveryAttempt
  -> optional MessageAttachment / MessageDocumentLink
```

Therefore analytics must cover that complete funnel rather than create a second "notification" reporting model.

## Coverage matrix

| Business question | Source of truth | Reporting contract | Status |
| --- | --- | --- | --- |
| How many recipients were selected / sent / failed / skipped / retried? | `campaign_runs` | `/summary` | Covered |
| What is the current message state? | `messages` | `/summary`, `/operations` | Covered |
| How do results change over time? | run/message facts | `/timeseries` | Covered |
| Which channels are used and how do they perform? | campaigns/messages | `/channels` | Covered |
| Which users initiated campaigns? | `campaigns.created_by` | `/users` | Covered |
| Which campaigns perform well or badly? | campaign runs | `/campaigns` | Covered |
| Which provider/business errors dominate? | delivery attempts | `/failures` | Covered |
| What happened to public generated documents? | message document links | `/documents` | Covered |
| What lifecycle state are mailings and runs in? | campaigns / campaign runs | `/lifecycle` | Covered in R1.2 |
| Are scheduled mailings pending, future, overdue or dispatched? | campaign scheduling fields | `/lifecycle` | Covered in R1.2 |
| How did the audience move through snapshot/eligible/skipped? | campaign recipients | `/audience` | Covered in R1.2 |
| Why were recipients skipped? | `campaign_recipients.skip_reason` | `/audience` | Covered in R1.2 |
| What happened at provider-attempt level? | message delivery attempts | `/attempts` | Covered in R1.2 |
| Are required attachments blocking delivery? | message attachments | `/attachments` | Covered in R1.2 |
| Are messages stuck, retry-due, unknown or queueing too long? | messages | `/operations` | Covered in R1.2 |
| Is Rabbit dead-letter depth growing? | RabbitMQ queue | Micrometer operational metrics | Covered operationally, not tenant business analytics |
| Is KumoMTA/provider health degraded? | delivery metrics / provider integration | Micrometer + failure/attempt reports | Covered |
| Which template is used by each campaign/message? | campaign/message template references | drill-down APIs, not aggregate KPI | Source available; aggregate not required yet |
| Open/click tracking | no persisted events | none | Not supported by source model |
| SMS/WhatsApp provider delivery receipts after provider acceptance | no persisted receipt event model | none | Not supported by source model |
| Direct one-off notification outside Campaign | no such domain path | none | Not applicable to current model |

## Tenant isolation

Tenant endpoints never accept an authoritative tenant override.

```text
/api/v1/analytics/communication/**
        -> TenantContext.requireTenantId()
```

Platform super-admin endpoints use the same metric definitions with optional tenant scope.

## New R1.2 endpoints

Tenant:

- `GET /api/v1/analytics/communication/lifecycle`
- `GET /api/v1/analytics/communication/audience`
- `GET /api/v1/analytics/communication/attempts`
- `GET /api/v1/analytics/communication/attachments`
- `GET /api/v1/analytics/communication/operations`

Platform equivalents are available under:

```text
/api/v1/platform/analytics/communication/**
```

## Metric semantics

### Lifecycle

Campaigns are counted by `campaigns.created_at` within the requested reporting window.

Run states are counted by `campaign_runs.created_at` within the window.

Scheduled campaign metrics distinguish:

- scheduled;
- active pending dispatch;
- future pending dispatch;
- overdue pending dispatch;
- dispatched.

### Audience

Recipients are counted by `campaign_recipients.created_at` in the requested window.

Statuses:

- SNAPSHOT;
- ELIGIBLE;
- SKIPPED.

Skip reasons are normalized to `UNCLASSIFIED` when absent or blank.

### Delivery attempts

Attempts are counted by `message_delivery_attempts.started_at`.

Statuses:

- STARTED;
- ACCEPTED;
- RETRYABLE_FAILURE;
- PERMANENT_FAILURE;
- UNKNOWN.

Accepted rate excludes still-open STARTED attempts from the denominator.

### Attachments

Attachments are counted by creation time.

The report includes:

- PENDING;
- READY;
- FAILED;
- required PENDING;
- required FAILED;
- failure-code distribution.

This makes attachment-generation failures visible independently from provider delivery failures.

### Operations

Operational health uses current message state constrained to the selected reporting scope.

It exposes:

- queued;
- processing;
- retry wait;
- unknown;
- failed;
- processing older than the configured processing timeout;
- retries already due;
- oldest queue age;
- oldest stuck-processing age;
- oldest overdue-retry age.

## Deliberate boundaries

### Rabbit DLQ

Rabbit dead-letter depth is a broker-level global operational signal and cannot be attributed safely to one tenant with the current queue model.

It therefore remains in Micrometer and is not exposed as a tenant KPI.

### Opens / clicks / provider receipts

The current persistence model does not store:

- email open events;
- click events;
- unsubscribe events;
- provider delivery/read receipts after initial provider acceptance.

Reporting must not invent those metrics.

If those product capabilities are added later, they require explicit event persistence before analytics is implemented.

## Performance

R1.2 adds bounded indexes for the new access patterns:

- campaign lifecycle by tenant/time/status/channel;
- run lifecycle by tenant/time/status;
- audience funnel by tenant/time/status;
- attempts by tenant/time/status;
- attachments by tenant/time/status.

R1.1 projections continue to accelerate historical delivery KPIs. R1.2 lifecycle/operations reports remain raw because they are low-cardinality bounded scans or mutable operational state.
