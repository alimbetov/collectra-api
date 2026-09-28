package io.collectra.api.reporting.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FinancialProjectionRebuildService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final FinancialProjectionStateService state;
    private final CommunicationProjectionProperties properties;
    private final TransactionTemplate transactions;

    public FinancialProjectionRebuildService(
            NamedParameterJdbcTemplate jdbc,
            Clock clock,
            FinancialProjectionStateService state,
            CommunicationProjectionProperties properties,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.state = state;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public RebuildResult rebuildTenantDay(UUID tenantId, LocalDate day) {
        Instant at = clock.instant();
        Instant from = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var p =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("day", day)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to))
                        .addValue("at", Timestamp.from(at));

        if (!state.tryMarkBuilding(tenantId, day, at, at.minus(properties.getBuildingTimeout()))) {
            return new RebuildResult(tenantId, day, 0, null, at, true);
        }
        try {
            return transactions.execute(
                    tx -> {
                        jdbc.update(
                                "delete from tenant_daily_financial_metrics where tenant_id=:tenantId and business_date=:day",
                                p);
                        int rows = rebuildMetrics(p);
                        Instant watermark = sourceWatermark(p);
                        p.addValue(
                                        "watermark",
                                        watermark == null ? null : Timestamp.from(watermark))
                                .addValue("rows", rows);
                        jdbc.update(
                                """
                        update tenant_financial_projection_state
                           set status='READY', revision=revision+1, source_watermark=:watermark,
                               metric_rows=:rows, calculated_at=:at, error_message=null
                         where tenant_id=:tenantId and business_date=:day
                        """,
                                p);
                        return new RebuildResult(tenantId, day, rows, watermark, at, false);
                    });
        } catch (RuntimeException ex) {
            state.markFailed(tenantId, day, at, ex.getMessage());
            throw ex;
        }
    }

    private int rebuildMetrics(MapSqlParameterSource p) {
        return jdbc.update(
                """
                insert into tenant_daily_financial_metrics(
                    business_date,tenant_id,currency,invoiced_amount,invoice_count,
                    payment_amount,payment_count,allocated_amount,reversed_allocation_amount,
                    collection_opened_count,collection_closed_count,collection_resolved_count,
                    calculated_at)
                with currencies as (
                    select currency from invoices
                     where tenant_id=:tenantId and invoice_date=:day
                    union
                    select currency from payments
                     where tenant_id=:tenantId and payment_date=:day
                    union
                    select p.currency from payment_allocations a join payments p on p.id=a.payment_id
                     where a.tenant_id=:tenantId
                       and ((a.created_at>=:from and a.created_at<:to)
                         or (a.reversed_at>=:from and a.reversed_at<:to))
                    union
                    select i.currency from collection_cases c join invoices i on i.id=c.invoice_id
                     where c.tenant_id=:tenantId
                       and ((c.opened_at>=:from and c.opened_at<:to)
                         or (c.closed_at>=:from and c.closed_at<:to))
                ),
                inv as (
                    select currency,sum(original_amount) amount,count(*) cnt from invoices
                     where tenant_id=:tenantId and invoice_date=:day group by currency
                ),
                pay as (
                    select currency,sum(amount) amount,count(*) cnt from payments
                     where tenant_id=:tenantId and payment_date=:day group by currency
                ),
                alloc as (
                    select p.currency,
                           coalesce(sum(a.amount) filter (where a.created_at>=:from and a.created_at<:to),0) allocated,
                           coalesce(sum(a.amount) filter (where a.status='REVERSED' and a.reversed_at>=:from and a.reversed_at<:to),0) reversed
                      from payment_allocations a join payments p on p.id=a.payment_id
                     where a.tenant_id=:tenantId
                       and ((a.created_at>=:from and a.created_at<:to)
                         or (a.reversed_at>=:from and a.reversed_at<:to))
                     group by p.currency
                ),
                cases as (
                    select i.currency,
                           count(*) filter (where c.opened_at>=:from and c.opened_at<:to) opened,
                           count(*) filter (where c.closed_at>=:from and c.closed_at<:to) closed,
                           count(*) filter (where c.closed_at>=:from and c.closed_at<:to
                                             and c.close_reason in ('PAID','SETTLED')) resolved
                      from collection_cases c join invoices i on i.id=c.invoice_id
                     where c.tenant_id=:tenantId
                       and ((c.opened_at>=:from and c.opened_at<:to)
                         or (c.closed_at>=:from and c.closed_at<:to))
                     group by i.currency
                )
                select :day,:tenantId,c.currency,
                       coalesce(i.amount,0),coalesce(i.cnt,0),
                       coalesce(p.amount,0),coalesce(p.cnt,0),
                       coalesce(a.allocated,0),coalesce(a.reversed,0),
                       coalesce(k.opened,0),coalesce(k.closed,0),coalesce(k.resolved,0),:at
                  from currencies c
                  left join inv i using(currency)
                  left join pay p using(currency)
                  left join alloc a using(currency)
                  left join cases k using(currency)
                """,
                p);
    }

    private Instant sourceWatermark(MapSqlParameterSource p) {
        Timestamp value =
                jdbc.queryForObject(
                        """
                select max(ts) from (
                    select max(updated_at) ts from invoices where tenant_id=:tenantId and invoice_date=:day
                    union all
                    select max(updated_at) from payments where tenant_id=:tenantId and payment_date=:day
                    union all
                    select max(updated_at) from payment_allocations where tenant_id=:tenantId
                      and ((created_at>=:from and created_at<:to) or (reversed_at>=:from and reversed_at<:to))
                    union all
                    select max(updated_at) from collection_cases where tenant_id=:tenantId
                      and ((opened_at>=:from and opened_at<:to) or (closed_at>=:from and closed_at<:to))
                ) s
                """,
                        p,
                        Timestamp.class);
        return value == null ? null : value.toInstant();
    }

    public record RebuildResult(
            UUID tenantId,
            LocalDate businessDate,
            int metricRows,
            Instant sourceWatermark,
            Instant calculatedAt,
            boolean lockSkipped) {}
}
