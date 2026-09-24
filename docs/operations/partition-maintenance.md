# Partition Maintenance V2

Collectra maintains PostgreSQL RANGE partitions through a scheduled Spring service.

## Safety defaults

Partition maintenance is disabled by default and dry-run is enabled by default:

```yaml
collectra:
  partition-maintenance:
    enabled: false
    dry-run: true
    cron: "0 15 2 * * *"
    max-partitions-per-run: 400
    lock-timeout: 5s
    statement-timeout: 30s
    tables: []
```

The default cron runs at 02:15 in `collectra.business-zone`.

Recommended rollout:

1. Convert and validate the target parent table through an explicit Liquibase migration.
2. Add a table policy with `dry-run: true`.
3. Run at least one nightly cycle and inspect logs and Prometheus metrics.
4. Set the table policy to `dry-run: false` only after the expected create/drop plan is confirmed.
5. Use `CREATE_ONLY` unless automatic retention cleanup is explicitly required.

## Per-table policy

Each table can define its own partition lifecycle:

```yaml
collectra:
  partition-maintenance:
    enabled: true
    dry-run: true
    cron: "0 15 2 * * *"
    tables:
      - schema: public
        table: message
        partition-column: created_at
        granularity: MONTH
        mode: CREATE_ONLY
        create-ahead: 3
        retention: 4
        retention-unit: YEARS

      - schema: public
        table: outbox_event
        partition-column: created_at
        granularity: DAY
        mode: CREATE_AND_DROP
        create-ahead: 14
        retention: 90
        retention-unit: DAYS
        dry-run: true
```

The table names above are policy examples. A policy must only be enabled after the actual table has been migrated to PostgreSQL `PARTITION BY RANGE`.

## Granularity

`DAY` creates partitions named `<table>_yyyyMMdd`.

`MONTH` creates partitions named `<table>_yyyyMM`.

Daily partitions are appropriate for high-churn technical tables with relatively short retention. Monthly partitions are preferable for long-lived high-volume business history such as messages.

## Lifecycle modes

`CREATE_ONLY` creates the current partition plus `create-ahead` future partitions and never drops data.

`CREATE_AND_DROP` also drops managed partitions whose upper bound is at or before the calculated retention cutoff.

Cleanup is deliberately conservative:

- only direct child partitions are considered;
- the partition name must match the configured granularity;
- PostgreSQL partition bounds must match the expected dates;
- malformed or unexpected partitions are skipped;
- dry-run reports the operation without executing DDL.

## Concurrency and database safety

Each parent table uses a PostgreSQL advisory lock based on `partition:<schema>.<table>`.

The service uses `pg_try_advisory_lock`, so another application instance does not wait indefinitely when a maintainer already owns the lock.

During maintenance the service applies bounded `lock_timeout` and `statement_timeout`. Existing connection-session values and PostgreSQL `TimeZone` are restored before the pooled connection is returned.

A global `max-partitions-per-run` guard prevents accidental creation of an excessive number of partitions.

## Failure isolation

A failure for one configured table is recorded in that table's result and metrics. Remaining table policies continue to execute during the same scheduler run.

## Metrics

The following Micrometer metrics are exported through the existing Actuator/Prometheus endpoint:

- `collectra.partition.maintenance.operations` with tags `table` and `action`;
- actions: `created`, `existing`, `dropped`, `planned_create`, `planned_drop`, `failed`, `lock_skipped`;
- `collectra.partition.maintenance.duration` timer tagged by `table` and `granularity`.

Operational alerting should at minimum cover:

- increase of `action=failed`;
- repeated `action=lock_skipped`;
- unexpected `planned_create` during a dry-run rollout;
- absence of successful nightly maintenance for configured production tables.

## Migration rule

The maintenance service does not convert a large non-partitioned table into a partitioned parent.

That operation must remain an explicit, reviewed migration because it can require data copying, index recreation, constraint changes and carefully controlled locking. The runtime scheduler only maintains an already validated RANGE-partitioned parent.
