package io.collectra.api.dashboard.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardQueryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId businessZone;

    public DashboardQueryService(JdbcTemplate jdbc, Clock clock, ZoneId businessZone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID tenantId) {
        Snapshot snapshot = snapshot();
        long customers = count("SELECT COUNT(*) FROM customers WHERE tenant_id = ?", tenantId);
        long activeContracts =
                count(
                        "SELECT COUNT(*) FROM contracts WHERE tenant_id = ? AND status = 'ACTIVE'",
                        tenantId);
        long openCollectionCases =
                count(
                        "SELECT COUNT(*) FROM collection_cases WHERE tenant_id = ? AND status <> 'CLOSED'",
                        tenantId);
        long activeCampaigns =
                count(
                        "SELECT COUNT(*) FROM campaigns WHERE tenant_id = ? AND status = 'ACTIVE'",
                        tenantId);
        return new Summary(
                snapshot.asOf(),
                snapshot.businessDate(),
                customers,
                activeContracts,
                openCollectionCases,
                activeCampaigns,
                receivableTotals(tenantId));
    }

    @Transactional(readOnly = true)
    public Receivables receivables(UUID tenantId) {
        Snapshot snapshot = snapshot();
        LocalDate businessDate = snapshot.businessDate();
        LocalDate dueSoonTo = businessDate.plusDays(7);

        Map<String, CurrencyReceivables> byCurrency = new LinkedHashMap<>();
        jdbc.query(
                """
                SELECT currency,
                       COALESCE(SUM(outstanding_amount), 0) AS outstanding,
                       COUNT(*) FILTER (WHERE due_date = ? AND outstanding_amount > 0
                           AND payment_status NOT IN ('PAID','CANCELLED')) AS due_today,
                       COUNT(*) FILTER (WHERE due_date > ? AND due_date <= ? AND outstanding_amount > 0
                           AND payment_status NOT IN ('PAID','CANCELLED')) AS due_soon,
                       COALESCE(SUM(outstanding_amount) FILTER (WHERE due_date >= ?), 0) AS current_amount,
                       COALESCE(SUM(outstanding_amount) FILTER (WHERE due_date < ? AND due_date >= ?), 0) AS days_1_30,
                       COALESCE(SUM(outstanding_amount) FILTER (WHERE due_date < ? AND due_date >= ?), 0) AS days_31_60,
                       COALESCE(SUM(outstanding_amount) FILTER (WHERE due_date < ? AND due_date >= ?), 0) AS days_61_90,
                       COALESCE(SUM(outstanding_amount) FILTER (WHERE due_date < ?), 0) AS days_90_plus
                FROM invoices
                WHERE tenant_id = ? AND outstanding_amount > 0 AND payment_status NOT IN ('PAID','CANCELLED')
                GROUP BY currency
                ORDER BY currency
                """,
                rs -> {
                    while (rs.next()) {
                        String currency = rs.getString("currency");
                        byCurrency.put(
                                currency,
                                new CurrencyReceivables(
                                        currency,
                                        rs.getBigDecimal("outstanding"),
                                        rs.getLong("due_today"),
                                        rs.getLong("due_soon"),
                                        new Aging(
                                                rs.getBigDecimal("current_amount"),
                                                rs.getBigDecimal("days_1_30"),
                                                rs.getBigDecimal("days_31_60"),
                                                rs.getBigDecimal("days_61_90"),
                                                rs.getBigDecimal("days_90_plus"))));
                    }
                },
                businessDate,
                businessDate,
                dueSoonTo,
                businessDate,
                businessDate,
                businessDate.minusDays(30),
                businessDate.minusDays(30),
                businessDate.minusDays(60),
                businessDate.minusDays(60),
                businessDate.minusDays(90),
                businessDate.minusDays(90),
                tenantId);
        return new Receivables(snapshot.asOf(), businessDate, List.copyOf(byCurrency.values()));
    }

    @Transactional(readOnly = true)
    public Delivery delivery(UUID tenantId) {
        Snapshot snapshot = snapshot();
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(recipient_count),0) recipient_count,
                       COALESCE(SUM(sent_count),0) sent_count,
                       COALESCE(SUM(failed_count),0) failed_count,
                       COALESCE(SUM(skipped_count),0) skipped_count,
                       COALESCE(SUM(retry_count),0) retry_count
                FROM campaign_runs
                WHERE tenant_id = ?
                """,
                (rs, rowNum) ->
                        new Delivery(
                                snapshot.asOf(),
                                snapshot.businessDate(),
                                rs.getLong("recipient_count"),
                                rs.getLong("sent_count"),
                                rs.getLong("failed_count"),
                                rs.getLong("skipped_count"),
                                rs.getLong("retry_count")),
                tenantId);
    }

    @Transactional(readOnly = true)
    public Collections collections(UUID tenantId) {
        Snapshot snapshot = snapshot();
        Instant now = snapshot.asOf();
        LocalDate businessDate = snapshot.businessDate();
        long activeCases =
                count(
                        "SELECT COUNT(*) FROM collection_cases WHERE tenant_id = ? AND status IN ('OPEN','IN_PROGRESS','ON_HOLD')",
                        tenantId);
        long overdueActions =
                count(
                        "SELECT COUNT(*) FROM collection_actions WHERE tenant_id = ? AND status = 'PENDING' AND due_at < ?",
                        tenantId,
                        now);
        long activePromisesDue =
                count(
                        "SELECT COUNT(*) FROM promises_to_pay WHERE tenant_id = ? AND status = 'ACTIVE' AND promised_date = ?",
                        tenantId,
                        businessDate);
        long activePromisesOverdue =
                count(
                        "SELECT COUNT(*) FROM promises_to_pay WHERE tenant_id = ? AND status = 'ACTIVE' AND promised_date < ?",
                        tenantId,
                        businessDate);
        long brokenPromises =
                count(
                        "SELECT COUNT(*) FROM promises_to_pay WHERE tenant_id = ? AND status = 'BROKEN'",
                        tenantId);
        long openDisputes =
                count(
                        "SELECT COUNT(*) FROM collection_disputes WHERE tenant_id = ? AND status = 'OPEN'",
                        tenantId);
        return new Collections(
                now,
                businessDate,
                activeCases,
                overdueActions,
                activePromisesDue,
                activePromisesOverdue,
                brokenPromises,
                openDisputes);
    }

    private List<CurrencyTotal> receivableTotals(UUID tenantId) {
        return jdbc.query(
                """
                SELECT currency, COALESCE(SUM(outstanding_amount),0) amount
                FROM invoices
                WHERE tenant_id = ? AND outstanding_amount > 0 AND payment_status NOT IN ('PAID','CANCELLED')
                GROUP BY currency ORDER BY currency
                """,
                (rs, rowNum) ->
                        new CurrencyTotal(rs.getString("currency"), rs.getBigDecimal("amount")),
                tenantId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private Snapshot snapshot() {
        Instant asOf = clock.instant();
        return new Snapshot(asOf, LocalDate.ofInstant(asOf, businessZone));
    }

    private record Snapshot(Instant asOf, LocalDate businessDate) {}

    public record CurrencyTotal(String currency, BigDecimal amount) {}

    public record Summary(
            Instant asOf,
            LocalDate businessDate,
            long customers,
            long activeContracts,
            long openCollectionCases,
            long activeCampaigns,
            List<CurrencyTotal> outstandingByCurrency) {}

    public record Aging(
            BigDecimal current,
            BigDecimal days1To30,
            BigDecimal days31To60,
            BigDecimal days61To90,
            BigDecimal days90Plus) {}

    public record CurrencyReceivables(
            String currency,
            BigDecimal outstanding,
            long dueToday,
            long dueSoon,
            Aging aging) {}

    public record Receivables(
            Instant asOf, LocalDate businessDate, List<CurrencyReceivables> currencies) {}

    public record Delivery(
            Instant asOf,
            LocalDate businessDate,
            long recipients,
            long sent,
            long failed,
            long skipped,
            long retries) {}

    public record Collections(
            Instant asOf,
            LocalDate businessDate,
            long activeCases,
            long overdueActions,
            long activePromisesDue,
            long activePromisesOverdue,
            long brokenPromises,
            long openDisputes) {}
}
