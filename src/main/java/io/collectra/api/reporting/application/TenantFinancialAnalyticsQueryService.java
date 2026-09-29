package io.collectra.api.reporting.application;

import java.math.BigDecimal;
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
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            for (Metric metric : dayMetrics(tenantId, day)) {
                totals.computeIfAbsent(metric.currency(), Mutable::new).add(metric);
            }
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
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            BucketKey key = bucketKey(day, bucket);
            Map<String, Mutable> values =
                    grouped.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            for (Metric metric : dayMetrics(tenantId, day)) {
                values.computeIfAbsent(metric.currency(), Mutable::new).add(metric);
            }
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

    private List<Metric> dayMetrics(UUID tenantId, LocalDate day) {
        if (day.isBefore(today()) && projectionReady(tenantId, day)) {
            return projectedDay(tenantId, day);
        }
        return rawDay(tenantId, day);
    }

    private boolean projectionReady(UUID tenantId, LocalDate day) {
        Integer count =
                jdbc.queryForObject(
                        """
                select count(*) from tenant_financial_projection_state
                 where tenant_id=? and business_date=? and status='READY'
                """,
                        Integer.class,
                        tenantId,
                        day);
        return count != null && count == 1;
    }

    private List<Metric> projectedDay(UUID tenantId, LocalDate day) {
        return jdbc.query(
                """
                select currency,invoiced_amount,invoice_count,payment_amount,payment_count,
                       allocated_amount,reversed_allocation_amount,collection_opened_count,
                       collection_closed_count,collection_resolved_count
                  from tenant_daily_financial_metrics
                 where tenant_id=? and business_date=? order by currency
                """,
                (rs, n) ->
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
                                rs.getLong("collection_resolved_count")),
                tenantId,
                day);
    }

    private List<Metric> rawDay(UUID tenantId, LocalDate day) {
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Timestamp fromTs = Timestamp.from(from);
        Timestamp toTs = Timestamp.from(to);
        return jdbc.query(
                """
                with currencies as (
                    select currency from invoices where tenant_id=? and invoice_date=?
                    union select currency from payments where tenant_id=? and payment_date=?
                    union select p.currency from payment_allocations a join payments p on p.id=a.payment_id
                     where a.tenant_id=? and ((a.created_at>=? and a.created_at<?) or (a.reversed_at>=? and a.reversed_at<?))
                    union select i.currency from collection_cases c join invoices i on i.id=c.invoice_id
                     where c.tenant_id=? and ((c.opened_at>=? and c.opened_at<?) or (c.closed_at>=? and c.closed_at<?))
                )
                select c.currency,
                    coalesce((select sum(i.original_amount) from invoices i where i.tenant_id=? and i.currency=c.currency and i.invoice_date=?),0) invoiced,
                    (select count(*) from invoices i where i.tenant_id=? and i.currency=c.currency and i.invoice_date=?) invoice_count,
                    coalesce((select sum(p.amount) from payments p where p.tenant_id=? and p.currency=c.currency and p.payment_date=?),0) payments,
                    (select count(*) from payments p where p.tenant_id=? and p.currency=c.currency and p.payment_date=?) payment_count,
                    coalesce((select sum(a.amount) from payment_allocations a join payments p on p.id=a.payment_id where a.tenant_id=? and p.currency=c.currency and a.created_at>=? and a.created_at<?),0) allocated,
                    coalesce((select sum(a.amount) from payment_allocations a join payments p on p.id=a.payment_id where a.tenant_id=? and p.currency=c.currency and a.status='REVERSED' and a.reversed_at>=? and a.reversed_at<?),0) reversed,
                    (select count(*) from collection_cases k join invoices i on i.id=k.invoice_id where k.tenant_id=? and i.currency=c.currency and k.opened_at>=? and k.opened_at<?) opened,
                    (select count(*) from collection_cases k join invoices i on i.id=k.invoice_id where k.tenant_id=? and i.currency=c.currency and k.closed_at>=? and k.closed_at<?) closed,
                    (select count(*) from collection_cases k join invoices i on i.id=k.invoice_id where k.tenant_id=? and i.currency=c.currency and k.close_reason in ('PAID','SETTLED') and k.closed_at>=? and k.closed_at<?) resolved
                from currencies c order by c.currency
                """,
                (rs, n) ->
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
                                rs.getLong("resolved")),
                tenantId,
                day,
                tenantId,
                day,
                tenantId,
                fromTs,
                toTs,
                fromTs,
                toTs,
                tenantId,
                fromTs,
                toTs,
                fromTs,
                toTs,
                tenantId,
                day,
                tenantId,
                day,
                tenantId,
                day,
                tenantId,
                day,
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
                fromTs,
                toTs);
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
                invoiced,
                invoices,
                payments,
                paymentCount,
                allocated,
                reversed,
                opened,
                closed,
                resolved,
                null);
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
