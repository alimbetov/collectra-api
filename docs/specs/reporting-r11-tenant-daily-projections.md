# R1.1 — Tenant Daily Reporting Projection Layer

## 1. Purpose

The communication reporting API remains the stable contract. Storage strategy is an internal implementation detail.

The architecture is intentionally split into:

```text
OLTP source of truth
    |
    +-- campaign_runs
    +-- messages
    +-- message_delivery_attempts
    +-- message_document_links
            |
            v
tenant/day rebuildable projections
            |
            v
CommunicationAnalyticsQueryService
            |
            +-- tenant API
            +-- platform API
```

A projection is a disposable derivative. It is never used by transactional business logic.

## 2. Grain

Every materialized row is tenant-scoped.

### Campaign/message metrics

`communication_daily_campaign_metrics`

Grain:

```text
UTC day + tenant + campaign
```

Dimensions stored at projection time:

- `tenant_id`
- `campaign_id`
- `created_by_user_id`
- `channel`

Facts:

- run count
- recipient count
- sent count
- failed count
- skipped count
- retry count
- message count
- QUEUED
- PROCESSING
- RETRY_WAIT
- message SENT
- message FAILED
- UNKNOWN
- last run timestamp

Document metrics are deliberately not stored in this projection. Document expiry and public-link access can mutate long after the seven-day reconciliation window.

### Failure metrics

`communication_daily_failure_metrics`

Grain:

```text
UTC day + tenant + campaign + normalized error code
```

Facts:

- failure count
- retryable count
- permanent count
- unknown count

### Projection state

`communication_reporting_projection_state`

Grain:

```text
tenant + UTC day
```

The state contains:

- BUILDING / READY / FAILED
- projection revision
- source watermark
- projected row counts
- calculated timestamp
- bounded error message

There is no global day watermark. One tenant failing must not invalidate another tenant.

## 3. Time semantics

R1 uses UTC buckets. R1.1 therefore also materializes UTC calendar days.

Tenant locale/timezone affects presentation, not metric ownership.

Weekly and monthly totals are computed from daily rows. There are deliberately no physical weekly or monthly tables yet.

A weekly or monthly summary therefore scans a small number of daily campaign rows instead of raw messages.

## 4. Rebuild semantics

A rebuild is idempotent.

For one `tenant + UTC day`:

1. the worker atomically claims `tenant + day` by setting state to `BUILDING` in an independent transaction;
2. a concurrent worker that sees a non-stale `BUILDING` claim skips the bucket;
3. an abandoned BUILDING claim may be taken over after the configured stale timeout;
4. existing projection rows for the tenant/day are deleted inside the rebuild transaction;
5. new rows are produced with `INSERT ... SELECT`;
6. source watermark is calculated;
7. state becomes `READY` and revision is incremented in the same rebuild transaction;
8. if the transaction fails, it rolls back completely and state becomes `FAILED` in a new transaction.

The previous materialized rows can remain physically present after a failed rebuild, but they are never selected because only `READY` days are eligible for projection routing.

## 5. Reconciliation

The scheduler rebuilds recent closed days for every active tenant.

Default policy:

```text
UTC 01:20
rolling window = D-1 .. D-7
tenant batch size = 100
stale BUILDING timeout = 30m
```

The rolling window covers delayed retry/recovery/provider reconciliation without requiring incremental event processing.

If late business-state changes beyond the reconciliation window become a real production pattern, the next optimization is a tenant/day dirty-bucket table. Do not increase projection complexity before observing that requirement.

## 6. Query routing

The REST contract never exposes whether the response came from raw or projected storage.

### Summary

For a mixed date range:

```text
leading partial UTC day -> raw
full READY UTC days     -> projection
trailing/current day    -> raw
                           |
                           v
                         merge
```

If any required historical tenant/day is missing or not READY, the entire request safely falls back to raw OLTP.

### Channels

Same hybrid strategy as summary.

### DAY / WEEK timeseries

Projection is used only when the requested interval:

- is aligned to complete UTC days;
- contains no current/open day;
- has READY state for every tenant/day;
- has no runId filter.

Otherwise raw OLTP is used.

### Users / campaigns / failures

Projection is used for complete closed READY UTC ranges.

Mixed/current ranges use raw queries so server pagination and ordering remain exact.

### Documents

Always raw.

Document expiry and public-link access are mutable long after document creation. Freezing those values into a seven-day projection would create silently stale reporting.

### HOUR timeseries

Always raw.

A daily projection cannot reconstruct hourly distribution.

### runId

Always raw.

The daily campaign projection does not contain run grain.

### Platform global reporting

Global cross-tenant reporting remains raw initially.

Platform reporting scoped to one `tenantId` can reuse the same tenant projection path.

## 7. Tenant isolation

Tenant reporting never accepts caller-controlled tenant scope.

```text
/api/v1/analytics/communication/**
        |
        v
TenantContext.requireTenantId()
```

Every projection query starts with `tenant_id`.

Platform super-admin endpoints may provide an explicit tenant scope, but use the same projection data and metric definitions.

## 8. Source-of-truth ownership

Metric definitions do not move into the projection layer.

Business outcomes:

```text
CampaignRun
recipient / sent / failed / skipped / retry
```

Operational state:

```text
Message
QUEUED / PROCESSING / RETRY_WAIT / SENT / FAILED / UNKNOWN
```

Failure classification:

```text
MessageDeliveryAttempt
error_code / RETRYABLE_FAILURE / PERMANENT_FAILURE / UNKNOWN
```

The projection layer only materializes these definitions.

## 9. Cache policy

Caching is deliberately not enabled in R1.1.

A cache may be added later only after observing repeated reads of already-cheap projections.

If introduced:

- never cache current-day/live operational metrics;
- never use TTL as the correctness mechanism;
- cache only closed READY projection ranges;
- include tenant scope, filters, date range and projection revision in the cache key;
- a projection revision change invalidates the corresponding cached view;
- raw fallback remains authoritative.

Recommended future cache key shape:

```text
report-type
+ tenant
+ from/to
+ dimensions/filters
+ projection revisions
```

## 10. Correctness gates

R1.1 is not complete unless integration tests prove:

- raw and projection summary equality;
- raw and projection channel equality;
- raw and projection DAY timeseries equality;
- raw and projection user equality;
- raw and projection campaign equality;
- raw and projection failure equality;
- one tenant projection never affects another tenant;
- missing READY state causes raw fallback;
- rebuild is idempotent;
- projection revision increases only after successful rebuild;
- failed rebuild never exposes a partial projection;
- existing OpenAPI contracts remain compatible.

## 11. Future scaling path

Do not introduce ClickHouse, Kafka Streams, CDC or additional OLAP infrastructure by default.

The escalation path is:

1. PostgreSQL tenant/day projections;
2. measure latency, table growth and rebuild cost;
3. add dirty tenant/day buckets if reconciliation cost becomes material;
4. add cache only for repeated closed-range reads if hit rate justifies it;
5. add weekly/monthly physical rollups only if daily scans become measurable;
6. move to a dedicated analytical store only when PostgreSQL projection workload is demonstrably insufficient.
