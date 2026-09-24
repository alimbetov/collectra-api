package io.collectra.api.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationProjectionQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId businessZone;

    public CommunicationProjectionQueryService(
            NamedParameterJdbcTemplate jdbc, ZoneId businessZone) {
        this.jdbc = jdbc;
        this.businessZone = businessZone;
    }

    @Transactional(readOnly = true)
    public boolean coversTenantRange(UUID tenantId, Instant from, Instant to) {
        LocalDate first = from.atZone(businessZone).toLocalDate();
        LocalDate lastExclusive = to.atZone(businessZone).toLocalDate();

        if (!to.atZone(businessZone).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            lastExclusive = lastExclusive.plusDays(1);
        }

        LocalDate today = LocalDate.now(java.time.Clock.system(businessZone));
        if (!lastExclusive.isBefore(today.plusDays(1))) {
            return false;
        }

        long expected = java.time.temporal.ChronoUnit.DAYS.between(first, lastExclusive);
        if (expected <= 0) return false;

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("fromDate", first)
                        .addValue("toDate", lastExclusive);

        Long ready =
                jdbc.queryForObject(
                        """
                        select count(*)
                          from communication_reporting_projection_state
                         where tenant_id = :tenantId
                           and business_date >= :fromDate
                           and business_date < :toDate
                           and status = 'READY'
                        """,
                        params,
                        Long.class);
        return ready != null && ready == expected;
    }

    @Transactional(readOnly = true)
    public CommunicationAnalyticsQueryService.BusinessTotals businessTotals(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId) {
        MapSqlParameterSource params = params(tenantId, from, to, campaignId, channel, userId);
        return jdbc.queryForObject(
                """
                select coalesce(sum(recipient_count), 0) recipient_count,
                       coalesce(sum(sent_count), 0) sent_count,
                       coalesce(sum(failed_count), 0) failed_count,
                       coalesce(sum(skipped_count), 0) skipped_count,
                       coalesce(sum(retry_count), 0) retry_count
                  from communication_daily_campaign_metrics
                 where tenant_id = :tenantId
                   and business_date >= :fromDate
                   and business_date < :toDate
                   and (:campaignId is null or campaign_id = :campaignId)
                   and (:channel is null or channel = :channel)
                   and (:userId is null or created_by_user_id = :userId)
                """,
                params,
                (rs, rowNum) ->
                        new CommunicationAnalyticsQueryService.BusinessTotals(
                                rs.getLong("recipient_count"),
                                rs.getLong("sent_count"),
                                rs.getLong("failed_count"),
                                rs.getLong("skipped_count"),
                                rs.getLong("retry_count")));
    }

    @Transactional(readOnly = true)
    public CommunicationAnalyticsQueryService.MessageStates messageStates(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId) {
        MapSqlParameterSource params = params(tenantId, from, to, campaignId, channel, userId);
        return jdbc.queryForObject(
                """
                select coalesce(sum(queued_count), 0) queued_count,
                       coalesce(sum(processing_count), 0) processing_count,
                       coalesce(sum(retry_wait_count), 0) retry_wait_count,
                       coalesce(sum(message_sent_count), 0) sent_count,
                       coalesce(sum(message_failed_count), 0) failed_count,
                       coalesce(sum(unknown_count), 0) unknown_count
                  from communication_daily_campaign_metrics
                 where tenant_id = :tenantId
                   and business_date >= :fromDate
                   and business_date < :toDate
                   and (:campaignId is null or campaign_id = :campaignId)
                   and (:channel is null or channel = :channel)
                   and (:userId is null or created_by_user_id = :userId)
                """,
                params,
                (rs, rowNum) ->
                        new CommunicationAnalyticsQueryService.MessageStates(
                                rs.getLong("queued_count"),
                                rs.getLong("processing_count"),
                                rs.getLong("retry_wait_count"),
                                rs.getLong("sent_count"),
                                rs.getLong("failed_count"),
                                rs.getLong("unknown_count")));
    }

    @Transactional(readOnly = true)
    public List<CommunicationAnalyticsQueryService.CommunicationChannelItem> channels(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId) {
        MapSqlParameterSource params = params(tenantId, from, to, campaignId, channel, userId);
        return jdbc.query(
                """
                select channel,
                       coalesce(sum(message_count), 0) message_count,
                       coalesce(sum(recipient_count), 0) recipient_count,
                       coalesce(sum(sent_count), 0) sent_count,
                       coalesce(sum(failed_count), 0) failed_count,
                       coalesce(sum(skipped_count), 0) skipped_count,
                       coalesce(sum(retry_count), 0) retry_count,
                       coalesce(sum(retry_wait_count), 0) retry_wait_count,
                       coalesce(sum(unknown_count), 0) unknown_count
                  from communication_daily_campaign_metrics
                 where tenant_id = :tenantId
                   and business_date >= :fromDate
                   and business_date < :toDate
                   and (:campaignId is null or campaign_id = :campaignId)
                   and (:channel is null or channel = :channel)
                   and (:userId is null or created_by_user_id = :userId)
                 group by channel
                 order by channel
                """,
                params,
                (rs, rowNum) -> {
                    long sent = rs.getLong("sent_count");
                    long failed = rs.getLong("failed_count");
                    return new CommunicationAnalyticsQueryService.CommunicationChannelItem(
                            rs.getString("channel"),
                            rs.getLong("message_count"),
                            rs.getLong("recipient_count"),
                            sent,
                            failed,
                            rs.getLong("skipped_count"),
                            rs.getLong("retry_count"),
                            rs.getLong("retry_wait_count"),
                            rs.getLong("unknown_count"),
                            rate(sent, failed));
                });
    }

    private MapSqlParameterSource params(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId) {
        LocalDate fromDate = from.atZone(businessZone).toLocalDate();
        LocalDate toDate = to.atZone(businessZone).toLocalDate();
        if (!to.atZone(businessZone).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            toDate = toDate.plusDays(1);
        }

        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("campaignId", campaignId)
                .addValue("channel", channel == null ? null : channel.trim().toUpperCase())
                .addValue("userId", userId);
    }

    private BigDecimal rate(long numerator, long otherTerminal) {
        long denominator = numerator + otherTerminal;
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
