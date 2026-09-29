# VC-9 — Tenant Analytics and Projections

Status: IMPLEMENTATION
Branch: `feat/vc9-tenant-analytics`

## 1. Objective

VC-9 turns the existing communication reporting foundation into tenant business analytics without moving analytical scans onto transactional hot paths.

The source of truth remains OLTP. All analytics projections are disposable and rebuildable.

```text
Receivables + Payments + Allocations + Collections + Communications
                              |
                              v
                   tenant/day projections
                              |
                     reporting query layer
                              |
                 tenant dashboard / reports
```

## 2. Existing baseline retained

VC-9 does not replace:

- `CommunicationAnalyticsQueryService`;
- communication daily projections;
- R1.1 raw/projection hybrid routing;
- R1.2 communication coverage;
- existing `/api/v1/dashboard/**` contracts.

Communication reporting remains the source for communication KPIs.

## 3. First projection grain

`tenant_daily_financial_metrics`

Grain:

```text
UTC day + tenant + currency
```

Closed-day facts:

- invoiced amount;
- payment amount;
- active allocation amount;
- reversed allocation amount;
- collection cases opened;
- collection cases closed;
- collection cases closed PAID/SETTLED.

Snapshot facts calculated at the end of the reporting day:

- outstanding amount;
- overdue outstanding amount;
- open invoice count;
- overdue invoice count;
- active collection case count.

A currency dimension is mandatory. Amounts of different currencies are never summed into one monetary KPI.

## 4. Projection state

`tenant_financial_projection_state`

Grain:

```text
tenant + UTC day
```

States:

- BUILDING;
- READY;
- FAILED.

State includes revision, source watermark, row count, calculated timestamp and bounded error text.

The rebuild contract follows communication projections:

1. independently claim tenant/day;
2. skip a live BUILDING claim;
3. reclaim stale BUILDING;
4. delete/rebuild tenant/day atomically;
5. mark READY and increment revision only in the successful transaction;
6. mark FAILED independently after rollback.

## 5. Time semantics

VC-9 uses UTC daily projection buckets, matching communication R1.1.

Current/open day remains raw.

Closed READY days may be served from projection.

WEEK and MONTH are aggregation windows over daily rows. No physical weekly/monthly tables are introduced until measured daily-row scans justify them.

## 6. Monetary correctness

Never aggregate different currencies.

Every monetary response either:

- returns per-currency values; or
- requires an explicit future FX conversion policy.

VC-9 does not invent FX rates.

Allocation metrics distinguish ACTIVE and REVERSED allocations. Reversed allocations must not be counted as recovered money.

## 7. Initial tenant analytics contract

Add tenant reporting endpoints under:

```text
/api/v1/analytics/tenant
```

Initial endpoints:

```text
GET /summary?from=&to=
GET /timeseries?from=&to=&bucket=DAY|WEEK|MONTH
```

Tenant scope comes exclusively from `TenantContext.requireTenantId()`.

The caller cannot override tenant identity.

### Summary

Per currency:

- invoiced;
- payments;
- allocated;
- reversed allocations;
- outstanding;
- overdue outstanding;
- open invoices;
- overdue invoices;
- collection cases opened;
- collection cases closed;
- collection cases resolved by PAID/SETTLED;
- recovery rate where the denominator is defined by the implementation contract.

### Timeseries

Each bucket contains the same per-currency business facts for trend analysis.

## 8. Query routing

```text
current/open UTC day       -> raw
closed READY UTC day       -> projection
missing/FAILED projection  -> safe raw fallback
mixed range                -> raw + projection only when exact semantics are preserved
```

A projection optimization must never change API values.

## 9. Tenant isolation

Every raw and projected SQL path begins with tenant scope.

Integration tests must prove:

- Alpha sees only Alpha;
- Beta changes cannot alter Alpha reports;
- caller-supplied tenant override is impossible;
- projection rebuild for Alpha cannot delete/update Beta rows.

## 10. Reconciliation

Default policy should mirror communication projection operations:

```text
daily scheduler
rolling D-1..D-7 rebuild
bounded tenant pages
stale BUILDING recovery
```

Late data outside the rolling window is handled by explicit rebuild first. A dirty-bucket mechanism is a later optimization if production evidence requires it.

## 11. Cache

No cache in the initial VC-9 slice.

Add cache only after measuring repeated expensive reads of immutable READY ranges. Cache keys must include tenant, filters, range and projection revisions.

## 12. Required executable gates

- raw/projection equality for summary;
- raw/projection equality for DAY/WEEK/MONTH;
- multi-currency isolation;
- tenant isolation;
- ACTIVE vs REVERSED allocation correctness;
- overdue boundary correctness;
- idempotent rebuild;
- failed rebuild exposes no partial projection;
- READY revision increments only on success;
- missing READY state falls back to raw;
- current day stays raw;
- existing communication analytics tests remain green;
- existing dashboard API remains compatible.

## 13. Follow-up VC-9 slices

After financial projections are proven:

1. collection effectiveness funnel;
2. contact-to-payment attribution with an explicit attribution contract;
3. dashboard migration from expensive OLTP aggregates to reporting query layer;
4. exports;
5. frontend tenant analytics screens;
6. performance/load gates;
7. dirty-bucket/cache/physical week-month rollups only from measured need.

## 14. Non-goals

Initial VC-9 does not introduce:

- ClickHouse;
- Kafka Streams;
- CDC;
- Elasticsearch analytics;
- physical week/month aggregate tables;
- cross-currency totals without FX semantics;
- invented open/click/provider-receipt metrics;
- analytics tables as transactional source of truth.
