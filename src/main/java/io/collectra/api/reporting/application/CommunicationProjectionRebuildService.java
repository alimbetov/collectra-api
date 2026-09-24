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
public class CommunicationProjectionRebuildService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final CommunicationProjectionStateService stateService;
    private final TransactionTemplate transactions;

    public CommunicationProjectionRebuildService(
            NamedParameterJdbcTemplate jdbc,
            Clock clock,
            CommunicationProjectionStateService stateService,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.stateService = stateService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public RebuildResult rebuildTenantDay(UUID tenantId, LocalDate businessDate) {
        Instant calculatedAt = clock.instant();
        Instant from = businessDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("businessDate", businessDate)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to))
                        .addValue("calculatedAt", Timestamp.from(calculatedAt));

        stateService.markBuilding(tenantId, businessDate, calculatedAt);

        try {
            return transactions.execute(
                    status -> {
                        jdbc.update(
                                """
                                delete from communication_daily_campaign_metrics
                                 where tenant_id = :tenantId
                                   and business_date = :businessDate
                                """,
                                params);

                        jdbc.update(
                                """
                                delete from communication_daily_failure_metrics
                                 where tenant_id = :tenantId
                                   and business_date = :businessDate
                                """,
                                params);

                        int campaignRows = rebuildCampaignMetrics(params);
                        int failureRows = rebuildFailureMetrics(params);
                        Instant watermark = sourceWatermark(params);

                        MapSqlParameterSource ready =
                                copy(params)
                                        .addValue(
                                                "sourceWatermark",
                                                watermark == null
                                                        ? null
                                                        : Timestamp.from(watermark))
                                        .addValue("campaignRows", campaignRows)
                                        .addValue("failureRows", failureRows);

                        jdbc.update(
                                """
                                update communication_reporting_projection_state
                                   set status = 'READY',
                                       revision = revision + 1,
                                       source_watermark = :sourceWatermark,
                                       campaign_rows = :campaignRows,
                                       failure_rows = :failureRows,
                                       calculated_at = :calculatedAt,
                                       error_message = null
                                 where tenant_id = :tenantId
                                   and business_date = :businessDate
                                """,
                                ready);

                        return new RebuildResult(
                                tenantId,
                                businessDate,
                                campaignRows,
                                failureRows,
                                watermark,
                                calculatedAt);
                    });
        } catch (RuntimeException ex) {
            stateService.markFailed(tenantId, businessDate, calculatedAt, ex.getMessage());
            throw ex;
        }
    }

    public LocalDate currentBusinessDate() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private int rebuildCampaignMetrics(MapSqlParameterSource params) {
        return jdbc.update(
                """
                insert into communication_daily_campaign_metrics(
                    business_date,
                    tenant_id,
                    campaign_id,
                    created_by_user_id,
                    channel,
                    run_count,
                    recipient_count,
                    sent_count,
                    failed_count,
                    skipped_count,
                    retry_count,
                    message_count,
                    queued_count,
                    processing_count,
                    retry_wait_count,
                    message_sent_count,
                    message_failed_count,
                    unknown_count,
                    document_created_count,
                    document_pending_count,
                    document_ready_count,
                    document_failed_count,
                    document_expired_count,
                    document_access_count,
                    document_accessed_link_count,
                    last_run_at,
                    calculated_at
                )
                with campaign_scope as (
                    select c.id campaign_id,
                           c.tenant_id,
                           c.created_by,
                           c.channel
                      from campaigns c
                     where c.tenant_id = :tenantId
                ),
                run_agg as (
                    select cr.campaign_id,
                           count(*) run_count,
                           coalesce(sum(cr.recipient_count), 0) recipient_count,
                           coalesce(sum(cr.sent_count), 0) sent_count,
                           coalesce(sum(cr.failed_count), 0) failed_count,
                           coalesce(sum(cr.skipped_count), 0) skipped_count,
                           coalesce(sum(cr.retry_count), 0) retry_count,
                           max(cr.created_at) last_run_at
                      from campaign_runs cr
                     where cr.tenant_id = :tenantId
                       and cr.created_at >= :from
                       and cr.created_at < :to
                     group by cr.campaign_id
                ),
                message_agg as (
                    select m.campaign_id,
                           count(*) message_count,
                           count(*) filter (where m.status = 'QUEUED') queued_count,
                           count(*) filter (where m.status = 'PROCESSING') processing_count,
                           count(*) filter (where m.status = 'RETRY_WAIT') retry_wait_count,
                           count(*) filter (where m.status = 'SENT') message_sent_count,
                           count(*) filter (where m.status = 'FAILED') message_failed_count,
                           count(*) filter (where m.status = 'UNKNOWN') unknown_count
                      from messages m
                     where m.tenant_id = :tenantId
                       and m.created_at >= :from
                       and m.created_at < :to
                     group by m.campaign_id
                ),
                document_agg as (
                    select m.campaign_id,
                           count(*) document_created_count,
                           count(*) filter (
                               where l.status = 'PENDING' and l.expires_at > :to
                           ) document_pending_count,
                           count(*) filter (
                               where l.status = 'READY' and l.expires_at > :to
                           ) document_ready_count,
                           count(*) filter (where l.status = 'FAILED') document_failed_count,
                           count(*) filter (where l.expires_at <= :to) document_expired_count,
                           coalesce(sum(l.access_count), 0) document_access_count,
                           count(*) filter (where l.access_count > 0) document_accessed_link_count
                      from message_document_links l
                      join messages m on m.id = l.message_id
                     where l.tenant_id = :tenantId
                       and l.created_at >= :from
                       and l.created_at < :to
                     group by m.campaign_id
                ),
                touched as (
                    select campaign_id from run_agg
                    union
                    select campaign_id from message_agg
                    union
                    select campaign_id from document_agg
                )
                select :businessDate,
                       c.tenant_id,
                       c.campaign_id,
                       c.created_by,
                       c.channel,
                       coalesce(r.run_count, 0),
                       coalesce(r.recipient_count, 0),
                       coalesce(r.sent_count, 0),
                       coalesce(r.failed_count, 0),
                       coalesce(r.skipped_count, 0),
                       coalesce(r.retry_count, 0),
                       coalesce(m.message_count, 0),
                       coalesce(m.queued_count, 0),
                       coalesce(m.processing_count, 0),
                       coalesce(m.retry_wait_count, 0),
                       coalesce(m.message_sent_count, 0),
                       coalesce(m.message_failed_count, 0),
                       coalesce(m.unknown_count, 0),
                       coalesce(d.document_created_count, 0),
                       coalesce(d.document_pending_count, 0),
                       coalesce(d.document_ready_count, 0),
                       coalesce(d.document_failed_count, 0),
                       coalesce(d.document_expired_count, 0),
                       coalesce(d.document_access_count, 0),
                       coalesce(d.document_accessed_link_count, 0),
                       r.last_run_at,
                       :calculatedAt
                  from touched t
                  join campaign_scope c on c.campaign_id = t.campaign_id
                  left join run_agg r on r.campaign_id = c.campaign_id
                  left join message_agg m on m.campaign_id = c.campaign_id
                  left join document_agg d on d.campaign_id = c.campaign_id
                """,
                params);
    }

    private int rebuildFailureMetrics(MapSqlParameterSource params) {
        return jdbc.update(
                """
                insert into communication_daily_failure_metrics(
                    business_date,
                    tenant_id,
                    campaign_id,
                    error_code,
                    failure_count,
                    retryable_count,
                    permanent_count,
                    unknown_count,
                    calculated_at
                )
                select :businessDate,
                       a.tenant_id,
                       m.campaign_id,
                       coalesce(a.error_code, 'UNCLASSIFIED'),
                       count(*),
                       count(*) filter (where a.status = 'RETRYABLE_FAILURE'),
                       count(*) filter (where a.status = 'PERMANENT_FAILURE'),
                       count(*) filter (where a.status = 'UNKNOWN'),
                       :calculatedAt
                  from message_delivery_attempts a
                  join messages m on m.id = a.message_id
                 where a.tenant_id = :tenantId
                   and a.completed_at >= :from
                   and a.completed_at < :to
                   and a.status in ('RETRYABLE_FAILURE','PERMANENT_FAILURE','UNKNOWN')
                 group by a.tenant_id, m.campaign_id, coalesce(a.error_code, 'UNCLASSIFIED')
                """,
                params);
    }

    private Instant sourceWatermark(MapSqlParameterSource params) {
        Timestamp value =
                jdbc.queryForObject(
                        """
                        select max(source_ts)
                          from (
                                select max(cr.updated_at) source_ts
                                  from campaign_runs cr
                                 where cr.tenant_id = :tenantId
                                   and cr.created_at >= :from
                                   and cr.created_at < :to
                                union all
                                select max(m.updated_at)
                                  from messages m
                                 where m.tenant_id = :tenantId
                                   and m.created_at >= :from
                                   and m.created_at < :to
                                union all
                                select max(a.completed_at)
                                  from message_delivery_attempts a
                                 where a.tenant_id = :tenantId
                                   and a.completed_at >= :from
                                   and a.completed_at < :to
                                union all
                                select max(greatest(l.updated_at, l.last_access_at))
                                  from message_document_links l
                                 where l.tenant_id = :tenantId
                                   and l.created_at >= :from
                                   and l.created_at < :to
                          ) x
                        """,
                        params,
                        Timestamp.class);
        return value == null ? null : value.toInstant();
    }

    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource copy = new MapSqlParameterSource();
        for (String name : source.getParameterNames()) {
            copy.addValue(name, source.getValue(name));
        }
        return copy;
    }

    public record RebuildResult(
            UUID tenantId,
            LocalDate businessDate,
            int campaignRows,
            int failureRows,
            Instant sourceWatermark,
            Instant calculatedAt) {}
}
