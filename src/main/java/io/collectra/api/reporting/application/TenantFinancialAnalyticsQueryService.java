package io.collectra.api.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantFinancialAnalyticsQueryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TenantFinancialAnalyticsQueryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Report summary(UUID tenantId, LocalDate from, LocalDate to) {
        Range range = range(from, to);
        Map<String, Mutable> totals = new LinkedHashMap<>();
        for (DatedMetric dated : rangeMetrics(tenantId, range)) {
            Metric metric = dated.metric();
            totals.computeIfAbsent(metric.currency(), Mutable::new).add(metric);
        }
        Map<String, Snapshot> snapshots = currentSnapshot(tenantId);
        snapshots.forEach(
                (currency, snapshot) ->
                        totals.computeIfAbsent(currency, Mutable::new).snapshot = snapshot);
        return new Report(
                clock.instant(),
                range.from(),
                range.to(),
                totals.values().stream()
                        .map(Mutable::freeze)
                        .sorted(Comparator.comparing(Metric::currency))
                        .toList());
    }

    @Transactional(readOnly = true)
    public TimeSeries timeseries(UUID tenantId, LocalDate from, LocalDate to, Bucket bucket) {
        Range range = range(from, to);
        Map<BucketKey, Map<String, Mutable>> grouped = new LinkedHashMap<>();
        for (DatedMetric dated : rangeMetrics(tenantId, range)) {
            BucketKey key = bucketKey(dated.day(), bucket);
            Map<String, Mutable> values =
                    grouped.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            Metric metric = dated.metric();
            values.computeIfAbsent(metric.currency(), Mutable::new).add(metric);
        }
        List<SeriesPoint> items =
                grouped.entrySet().stream()
                        .map(
                                e ->
                                        new SeriesPoint(
                                                e.getKey().from,
                                                e.getKey().to,
                                                e.getValue().values().stream()
                                                        .map(Mutable::freeze)
                                                        .sorted(
                                                                Comparator.comparing(
                                                                        Metric::currency))
                                                        .toList()))
                        .toList();
        return new TimeSeries(clock.instant(), range.from(), range.to(), bucket, items);
    }

    private List<DatedMetric> rangeMetrics(UUID tenantId, Range range) {
        LocalDate today = today();
        LocalDate closedTo = range.to().isBefore(today) ? range.to() : today.minusDays(1);
        Set<LocalDate> readyDays = readyProjectionDays(tenantId, range.from(), closedTo);

        List<DatedMetric> result = new java.util.ArrayList<>();
        if (!readyDays.isEmpty()) {
            result.addAll(projectedRange(tenantId, range.from(), closedTo));
        }
        result.addAll(rawRange(tenantId, range, readyDays));
        return result;
    }

    private Set<LocalDate> readyProjectionDays(UUID tenantId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return Set.of();
        }
        return new HashSet<>(
                jdbc.query(
                        """
                        select business_date
                          from tenant_financial_projection_state
                         where tenant_id=? and business_date between ? and ? and status='READY'
                        """,
                        (rs, n) -> rs.getObject("business_date", LocalDate.class),
                        tenantId,
                        from,
                        to));
    }

    private List<DatedMetric> projectedRange(UUID tenantId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return List.of();
        }
        return jdbc.query(
                """
                select m.business_date,m.currency,m.invoiced_amount,m.invoice_count,
                       m.payment_amount,m.payment_count,m.allocated_amount,
                       m.reversed_allocation_amount,m.collection_opened_count,
                       m.collection_closed_count,m.collection_resolved_count
                  from tenant_daily_financial_metrics m
                  join tenant_financial_projection_state s
                    on s.tenant_id=m.tenant_id and s.business_date=m.business_date
                   and s.status='READY'
                 where m.tenant_id=? and m.business_date between ? and ?
                 order by m.business_date,m.currency
                """,
                (rs, n) ->
                        new DatedMetric(
                                rs.getObject("business_date", LocalDate.class),
                                metric(
                                        rs.getString("currency"),
                                        rs.getBigDecimal("invoiced_amount"),
                                        rs.getLong("invoice_count"),
                                        rs.getBigDecimal("payment_amount"),
                                        rs.getLong("payment_count"),
                                        rs.getBigDecimal("allocated_amount"),
                                        rs.getBigDecimal("reversed_allocation_amount"),
                                        rs.getLong("collection_opened_count"),
                                        rs.getLong("collection_closed_count"),
                                        rs.getLong("collection_resolved_count"))),
                tenantId,
                from,
                to);
    }

    private List<DatedMetric> rawRange(UUID tenantId, Range range, Set<LocalDate> excludedDays) {
        Instant from = range.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = range.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Timestamp fromTs = Timestamp.from(from);
        Timestamp toTs = Timestamp.from(to);
        java.sql.Array excluded = null;
        try {
            excluded =
                    jdbc.getDataSource()
                            .getConnection()
                            .createArrayOf(
                                    "date",
                                    excludedDays.stream()
                                            .sorted()
                                            .map(java.sql.Date::valueOf)
                                            .toArray(java.sql.Date[]::new));
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Cannot bind financial projection days", ex);
        }
        try {
            return jdbc.query(
                    """
                    with days as (
                        select d::date business_date
                          from generate_series(?::date, ?::date, interval '1 day') d
                         where not (d::date = any (?::date[]))
                    ),
                    currencies as (
                        select i.invoice_date business_date,i.currency from invoices i join days d on d.business_date=i.invoice_date
                         where i.tenant_id=?
                        union
                        select p.payment_date,p.currency from payments p join days d on d.business_date=p.payment_date
                         where p.tenant_id=?
                        union
                        select (a.created_at at time zone 'UTC')::date,p.currency
                          from payment_allocations a join payments p on p.id=a.payment_id
                          join days d on d.business_date=(a.created_at at time zone 'UTC')::date
                         where a.tenant_id=? and a.created_at>=? and a.created_at<?
                        union
                        select (a.reversed_at at time zone 'UTC')::date,p.currency
                          from payment_allocations a join payments p on p.id=a.payment_id
                          join days d on d.business_date=(a.reversed_at at time zone 'UTC')::date
                         where a.tenant_id=? and a.reversed_at>=? and a.reversed_at<?
                        union
                        select (c.opened_at at time zone 'UTC')::date,i.currency
                          from collection_cases c join invoices i on i.id=c.invoice_id
                          join days d on d.business_date=(c.opened_at at time zone 'UTC')::date
                         where c.tenant_id=? and c.opened_at>=? and c.opened_at<?
                        union
                        select (c.closed_at at time zone 'UTC')::date,i.currency
                          from collection_cases c join invoices i on i.id=c.invoice_id
                          join days d on d.business_date=(c.closed_at at time zone 'UTC')::date
                         where c.tenant_id=? and c.closed_at>=? and c.closed_at<?
                    ),
                    inv as (
                        select i.invoice_date business_date,i.currency,sum(i.original_amount) amount,count(*) cnt
                          from invoices i join days d on d.business_date=i.invoice_date
                         where i.tenant_id=? group by i.invoice_date,i.currency
                    ),
                    pay as (
                        select p.payment_date business_date,p.currency,sum(p.amount) amount,count(*) cnt
                          from payments p join days d on d.business_date=p.payment_date
                         where p.tenant_id=? group by p.payment_date,p.currency
                    ),
                    alloc as (
                        select x.business_date,x.currency,
                               sum(x.allocated) allocated,sum(x.reversed) reversed
                          from (
                            select (a.created_at at time zone 'UTC')::date business_date,p.currency,a.amount allocated,0::numeric reversed
                              from payment_allocations a join payments p on p.id=a.payment_id
                              join days d on d.business_date=(a.created_at at time zone 'UTC')::date
                             where a.tenant_id=? and a.created_at>=? and a.created_at<?
                            union all
                            select (a.reversed_at at time zone 'UTC')::date,p.currency,0::numeric,a.amount
                              from payment_allocations a join payments p on p.id=a.payment_id
                              join days d on d.business_date=(a.reversed_at at time zone 'UTC')::date
                             where a.tenant_id=? and a.status='REVERSED' and a.reversed_at>=? and a.reversed_at<?
                          ) x group by x.business_date,x.currency
                    ),
                    cases as (
                        select x.business_date,x.currency,
                               sum(x.opened) opened,sum(x.closed) closed,sum(x.resolved) resolved
                          from (
                            select (c.opened_at at time zone 'UTC')::date business_date,i.currency,1 opened,0 closed,0 resolved
                              from collection_cases c join invoices i on i.id=c.invoice_id
                              join days d on d.business_date=(c.opened_at at time zone 'UTC')::date
                             where c.tenant_id=? and c.opened_at>=? and c.opened_at<?
                            union all
                            select (c.closed_at at time zone 'UTC')::date business_date,i.currency,0,1,
                                   case when c.close_reason in ('PAID','SETTLED') then 1 else 0 end
                              from collection_cases c join invoices i on i.id=c.invoice_id
                              join days d on d.business_date=(c.closed_at at time zone 'UTC')::date
                             where c.tenant_id=? and c.closed_at>=? and c.closed_at<?
                          ) x group by x.business_date,x.currency
                    )
                    select c.business_date,c.currency,
                           coalesce(i.amount,0) invoiced,coalesce(i.cnt,0) invoice_count,
                           coalesce(p.amount,0) payments,coalesce(p.cnt,0) payment_count,
                           coalesce(a.allocated,0) allocated,coalesce(a.reversed,0) reversed,
                           coalesce(k.opened,0) opened,coalesce(k.closed,0) closed,coalesce(k.resolved,0) resolved
                      from currencies c
                      left join inv i using(business_date,currency)
                      left join pay p using(business_date,currency)
                      left join alloc a using(business_date,currency)
                      left join cases k using(business_date,currency)
                     order by c.business_date,c.currency
                    """,
                    (rs, n) ->
                            new DatedMetric(
                                    rs.getObject("business_date", LocalDate.class),
                                    metric(
                                            rs.getString("currency"),
                                            rs.getBigDecimal("invoiced"),
                                            rs.getLong("invoice_count"),
                                            rs.getBigDecimal("payments"),
                                            rs.getLong("payment_count"),
                                            rs.getBigDecimal("allocated"),
                                            rs.getBigDecimal("reversed"),
                                            rs.getLong("opened"),
                                            rs.getLong("closed"),
                                            rs.getLong("resolved"))),
                    range.from(),
                    range.to(),
                    excluded,
                    tenantId,
                    tenantId,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    tenantId,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    fromTs,
                    toTs,
                    tenantId,
                    fromTs,
                    toTs);
        } finally {
            if (excluded != null) {
                try {
                    excluded.free();
                } catch (java.sql.SQLException ignored) {
                    // PostgreSQL driver resource cleanup only.
                }
            }
        }
    }

    private Map<String, Snapshot> currentSnapshot(UUID tenantId) {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        LocalDate today = today();
        jdbc.query(
                """
                select currency,
                       coalesce(sum(outstanding_amount),0) outstanding,
                       coalesce(sum(outstanding_amount) filter (where due_date < ?),0) overdue,
                       count(*) open_invoices,
                       count(*) filter (where due_date < ?) overdue_invoices
                  from invoices
                 where tenant_id=? and outstanding_amount>0 and payment_status not in ('PAID','CANCELLED')
                 group by currency
                """,
                (org.springframework.jdbc.core.RowCallbackHandler)
                        rs ->
                                result.put(
                                        rs.getString("currency"),
                                        new Snapshot(
                                                rs.getBigDecimal("outstanding"),
                                                rs.getBigDecimal("overdue"),
                                                rs.getLong("open_invoices"),
                                                rs.getLong("overdue_invoices"))),
                today,
                today,
                tenantId);
        return result;
    }

    private Metric metric(
            String currency,
            BigDecimal invoiced,
            long invoices,
            BigDecimal payments,
            long paymentCount,
            BigDecimal allocated,
            BigDecimal reversed,
            long opened,
            long closed,
            long resolved) {
        return new Metric(
                currency,
                money(invoiced),
                invoices,
                money(payments),
                paymentCount,
                money(allocated),
                money(reversed),
                opened,
                closed,
                resolved,
                null);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.UNNECESSARY);
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new IllegalArgumentException("from must not be after to");
        if (start.isBefore(end.minusYears(2)))
            throw new IllegalArgumentException("range must not exceed two years");
        return new Range(start, end);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private BucketKey bucketKey(LocalDate day, Bucket bucket) {
        return switch (bucket) {
            case DAY -> new BucketKey(day, day);
            case WEEK -> {
                LocalDate start = day.with(WeekFields.ISO.dayOfWeek(), 1);
                yield new BucketKey(start, start.plusDays(6));
            }
            case MONTH ->
                    new BucketKey(
                            day.withDayOfMonth(1),
                            day.withDayOfMonth(1).plusMonths(1).minusDays(1));
        };
    }

    public enum Bucket {
        DAY,
        WEEK,
        MONTH
    }

    public record Report(
            Instant generatedAt, LocalDate from, LocalDate to, List<Metric> currencies) {}

    public record TimeSeries(
            Instant generatedAt,
            LocalDate from,
            LocalDate to,
            Bucket bucket,
            List<SeriesPoint> items) {}

    public record SeriesPoint(LocalDate from, LocalDate to, List<Metric> currencies) {}

    public record Snapshot(
            BigDecimal outstanding,
            BigDecimal overdueOutstanding,
            long openInvoices,
            long overdueInvoices) {}

    public record Metric(
            String currency,
            BigDecimal invoiced,
            long invoiceCount,
            BigDecimal payments,
            long paymentCount,
            BigDecimal allocated,
            BigDecimal reversedAllocations,
            long collectionOpened,
            long collectionClosed,
            long collectionResolved,
            Snapshot currentSnapshot) {}

    private record Range(LocalDate from, LocalDate to) {}

    private record DatedMetric(LocalDate day, Metric metric) {}

    private record BucketKey(LocalDate from, LocalDate to) {}

    private static final class Mutable {
        private final String currency;
        private BigDecimal invoiced = BigDecimal.ZERO;
        private long invoices;
        private BigDecimal payments = BigDecimal.ZERO;
        private long paymentCount;
        private BigDecimal allocated = BigDecimal.ZERO;
        private BigDecimal reversed = BigDecimal.ZERO;
        private long opened;
        private long closed;
        private long resolved;
        private Snapshot snapshot;

        private Mutable(String currency) {
            this.currency = currency;
        }

        private void add(Metric m) {
            invoiced = invoiced.add(m.invoiced());
            invoices += m.invoiceCount();
            payments = payments.add(m.payments());
            paymentCount += m.paymentCount();
            allocated = allocated.add(m.allocated());
            reversed = reversed.add(m.reversedAllocations());
            opened += m.collectionOpened();
            closed += m.collectionClosed();
            resolved += m.collectionResolved();
        }

        private Metric freeze() {
            return new Metric(
                    currency,
                    invoiced,
                    invoices,
                    payments,
                    paymentCount,
                    allocated,
                    reversed,
                    opened,
                    closed,
                    resolved,
                    snapshot);
        }
    }
}
