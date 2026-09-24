package io.collectra.api.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationProjectionQueryService {
    private static final Map<String, String> USER_SORT =
            Map.of(
                    "sent", "sent_count",
                    "failed", "failed_count",
                    "recipients", "recipient_count",
                    "campaigns", "campaign_count",
                    "email", "email",
                    "displayName", "display_name");

    private static final Map<String, String> CAMPAIGN_SORT =
            Map.of(
                    "lastRunAt", "last_run_at",
                    "sent", "sent_count",
                    "failed", "failed_count",
                    "recipients", "recipient_count",
                    "runs", "run_count",
                    "name", "campaign_name");

    private static final Map<String, String> FAILURE_SORT =
            Map.of("count", "failure_count", "errorCode", "error_code");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CommunicationProjectionQueryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean coversTenantRange(UUID tenantId, Instant from, Instant to) {
        LocalDate first = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastExclusive = to.atZone(ZoneOffset.UTC).toLocalDate();

        if (!to.atZone(ZoneOffset.UTC).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            lastExclusive = lastExclusive.plusDays(1);
        }

        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
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
            UUID tenantId, Instant from, Instant to, UUID campaignId, String channel, UUID userId) {
        Query query = campaignQuery(tenantId, from, to, campaignId, channel, userId);
        return jdbc.queryForObject(
                """
                select coalesce(sum(recipient_count), 0) recipient_count,
                       coalesce(sum(sent_count), 0) sent_count,
                       coalesce(sum(failed_count), 0) failed_count,
                       coalesce(sum(skipped_count), 0) skipped_count,
                       coalesce(sum(retry_count), 0) retry_count
                  from communication_daily_campaign_metrics x
                """
                        + query.where(),
                query.params(),
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
            UUID tenantId, Instant from, Instant to, UUID campaignId, String channel, UUID userId) {
        Query query = campaignQuery(tenantId, from, to, campaignId, channel, userId);
        return jdbc.queryForObject(
                """
                select coalesce(sum(queued_count), 0) queued_count,
                       coalesce(sum(processing_count), 0) processing_count,
                       coalesce(sum(retry_wait_count), 0) retry_wait_count,
                       coalesce(sum(message_sent_count), 0) sent_count,
                       coalesce(sum(message_failed_count), 0) failed_count,
                       coalesce(sum(unknown_count), 0) unknown_count
                  from communication_daily_campaign_metrics x
                """
                        + query.where(),
                query.params(),
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
            UUID tenantId, Instant from, Instant to, UUID campaignId, String channel, UUID userId) {
        Query query = campaignQuery(tenantId, from, to, campaignId, channel, userId);
        return jdbc.query(
                """
                select x.channel channel,
                       coalesce(sum(message_count), 0) message_count,
                       coalesce(sum(recipient_count), 0) recipient_count,
                       coalesce(sum(sent_count), 0) sent_count,
                       coalesce(sum(failed_count), 0) failed_count,
                       coalesce(sum(skipped_count), 0) skipped_count,
                       coalesce(sum(retry_count), 0) retry_count,
                       coalesce(sum(retry_wait_count), 0) retry_wait_count,
                       coalesce(sum(unknown_count), 0) unknown_count
                  from communication_daily_campaign_metrics x
                """
                        + query.where()
                        + " group by x.channel order by x.channel",
                query.params(),
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

    @Transactional(readOnly = true)
    public List<CommunicationAnalyticsQueryService.CommunicationTimePoint> timeseries(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId,
            CommunicationAnalyticsQueryService.Bucket bucket) {
        Query query = campaignQuery(tenantId, from, to, campaignId, channel, userId);
        String bucketExpression =
                switch (bucket) {
                    case DAY -> "x.business_date::timestamp at time zone 'UTC'";
                    case WEEK ->
                            "date_trunc('week', x.business_date::timestamp) at time zone 'UTC'";
                    case HOUR ->
                            throw new IllegalArgumentException(
                                    "Hourly projection timeseries is not supported");
                };

        return jdbc.query(
                """
                select %s bucket,
                       coalesce(sum(recipient_count), 0) recipient_count,
                       coalesce(sum(sent_count), 0) sent_count,
                       coalesce(sum(failed_count), 0) failed_count,
                       coalesce(sum(skipped_count), 0) skipped_count,
                       coalesce(sum(retry_count), 0) retry_count,
                       coalesce(sum(queued_count), 0) queued_count,
                       coalesce(sum(processing_count), 0) processing_count,
                       coalesce(sum(retry_wait_count), 0) retry_wait_count,
                       coalesce(sum(message_sent_count), 0) message_sent_count,
                       coalesce(sum(message_failed_count), 0) message_failed_count,
                       coalesce(sum(unknown_count), 0) unknown_count
                  from communication_daily_campaign_metrics x
                """
                                .formatted(bucketExpression)
                        + query.where()
                        + " group by 1 order by 1",
                query.params(),
                (rs, rowNum) ->
                        new CommunicationAnalyticsQueryService.CommunicationTimePoint(
                                rs.getTimestamp("bucket").toInstant(),
                                rs.getLong("recipient_count"),
                                rs.getLong("sent_count"),
                                rs.getLong("failed_count"),
                                rs.getLong("skipped_count"),
                                rs.getLong("retry_count"),
                                rs.getLong("queued_count"),
                                rs.getLong("processing_count"),
                                rs.getLong("retry_wait_count"),
                                rs.getLong("message_sent_count"),
                                rs.getLong("message_failed_count"),
                                rs.getLong("unknown_count")));
    }

    @Transactional(readOnly = true)
    public ProjectionPage<CommunicationAnalyticsQueryService.CommunicationUserItem> users(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId,
            int page,
            int size,
            String sort) {
        Query query = campaignQuery(tenantId, from, to, campaignId, channel, userId);
        String grouped =
                """
                from communication_daily_campaign_metrics x
                join tenants t on t.id = x.tenant_id
                left join user_accounts ua on ua.id = x.created_by_user_id
                """
                        + query.where()
                        + """
                         group by x.tenant_id, t.name, x.created_by_user_id, ua.email, ua.display_name
                        """;

        Long total =
                jdbc.queryForObject(
                        "select count(*) from (select x.created_by_user_id " + grouped + ") q",
                        query.params(),
                        Long.class);

        MapSqlParameterSource params =
                copy(query.params())
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationAnalyticsQueryService.CommunicationUserItem> items =
                jdbc.query(
                        """
                        select x.created_by_user_id user_id,
                               coalesce(ua.email, 'SYSTEM') email,
                               coalesce(ua.display_name, 'SYSTEM') display_name,
                               x.tenant_id,
                               t.name tenant_name,
                               count(distinct x.campaign_id) filter (where x.run_count > 0)
                                       campaign_count,
                               coalesce(sum(x.recipient_count), 0) recipient_count,
                               coalesce(sum(x.sent_count), 0) sent_count,
                               coalesce(sum(x.failed_count), 0) failed_count,
                               coalesce(sum(x.skipped_count), 0) skipped_count,
                               coalesce(sum(x.retry_count), 0) retry_count,
                               coalesce(sum(x.unknown_count), 0) unknown_count
                        """
                                + grouped
                                + " order by "
                                + stableOrder(orderBy(sort, USER_SORT, "sent", "desc"), "user_id")
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) -> {
                            long sent = rs.getLong("sent_count");
                            long failed = rs.getLong("failed_count");
                            return new CommunicationAnalyticsQueryService.CommunicationUserItem(
                                    rs.getObject("user_id", UUID.class),
                                    rs.getString("email"),
                                    rs.getString("display_name"),
                                    rs.getObject("tenant_id", UUID.class),
                                    rs.getString("tenant_name"),
                                    rs.getLong("campaign_count"),
                                    rs.getLong("recipient_count"),
                                    sent,
                                    failed,
                                    rs.getLong("skipped_count"),
                                    rs.getLong("retry_count"),
                                    rs.getLong("unknown_count"),
                                    rate(sent, failed));
                        });
        return new ProjectionPage<>(items, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ProjectionPage<CommunicationAnalyticsQueryService.CommunicationCampaignItem> campaigns(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId,
            int page,
            int size,
            String sort) {
        Query query = campaignQuery(tenantId, from, to, campaignId, channel, userId);
        String grouped =
                """
                from communication_daily_campaign_metrics x
                join campaigns c on c.id = x.campaign_id
                join tenants t on t.id = x.tenant_id
                left join user_accounts ua on ua.id = x.created_by_user_id
                """
                        + query.where()
                        + " and x.run_count > 0"
                        + """
                         group by x.campaign_id, c.name, x.tenant_id, t.name,
                                  x.created_by_user_id, ua.email, x.channel
                        """;

        Long total =
                jdbc.queryForObject(
                        "select count(*) from (select x.campaign_id " + grouped + ") q",
                        query.params(),
                        Long.class);

        MapSqlParameterSource params =
                copy(query.params())
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationAnalyticsQueryService.CommunicationCampaignItem> items =
                jdbc.query(
                        """
                        select x.campaign_id,
                               c.name campaign_name,
                               x.tenant_id,
                               t.name tenant_name,
                               x.created_by_user_id created_by,
                               coalesce(ua.email, 'SYSTEM') created_by_email,
                               x.channel,
                               coalesce(sum(x.run_count), 0) run_count,
                               coalesce(sum(x.recipient_count), 0) recipient_count,
                               coalesce(sum(x.sent_count), 0) sent_count,
                               coalesce(sum(x.failed_count), 0) failed_count,
                               coalesce(sum(x.skipped_count), 0) skipped_count,
                               coalesce(sum(x.retry_count), 0) retry_count,
                               max(x.last_run_at) last_run_at
                        """
                                + grouped
                                + " order by "
                                + stableOrder(
                                        orderBy(sort, CAMPAIGN_SORT, "lastRunAt", "desc"),
                                        "campaign_id")
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) -> {
                            long sent = rs.getLong("sent_count");
                            long failed = rs.getLong("failed_count");
                            java.sql.Timestamp lastRunAt = rs.getTimestamp("last_run_at");
                            return new CommunicationAnalyticsQueryService.CommunicationCampaignItem(
                                    rs.getObject("campaign_id", UUID.class),
                                    rs.getString("campaign_name"),
                                    rs.getObject("tenant_id", UUID.class),
                                    rs.getString("tenant_name"),
                                    rs.getObject("created_by", UUID.class),
                                    rs.getString("created_by_email"),
                                    rs.getString("channel"),
                                    rs.getLong("run_count"),
                                    rs.getLong("recipient_count"),
                                    sent,
                                    failed,
                                    rs.getLong("skipped_count"),
                                    rs.getLong("retry_count"),
                                    lastRunAt == null ? null : lastRunAt.toInstant(),
                                    rate(sent, failed));
                        });
        return new ProjectionPage<>(items, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ProjectionPage<CommunicationAnalyticsQueryService.CommunicationFailureItem> failures(
            UUID tenantId,
            Instant from,
            Instant to,
            UUID campaignId,
            String channel,
            UUID userId,
            int page,
            int size,
            String sort) {
        LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("fromDate", fromDate)
                        .addValue("toDate", toDate);
        StringBuilder where =
                new StringBuilder(
                        """
                         where f.tenant_id = :tenantId
                           and f.business_date >= :fromDate
                           and f.business_date < :toDate
                        """);
        if (campaignId != null) {
            params.addValue("campaignId", campaignId);
            where.append(" and f.campaign_id = :campaignId");
        }
        if (channel != null && !channel.isBlank()) {
            params.addValue("channel", channel.trim().toUpperCase());
            where.append(" and c.channel = :channel");
        }
        if (userId != null) {
            params.addValue("userId", userId);
            where.append(" and c.created_by = :userId");
        }

        String grouped =
                """
                from communication_daily_failure_metrics f
                join campaigns c on c.id = f.campaign_id
                """
                        + where
                        + " group by f.error_code";

        Long total =
                jdbc.queryForObject(
                        "select count(*) from (select f.error_code " + grouped + ") q",
                        params,
                        Long.class);

        MapSqlParameterSource paged =
                copy(params)
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationAnalyticsQueryService.CommunicationFailureItem> items =
                jdbc.query(
                        """
                        select f.error_code,
                               coalesce(sum(f.failure_count), 0) failure_count,
                               coalesce(sum(f.retryable_count), 0) retryable_count,
                               coalesce(sum(f.permanent_count), 0) permanent_count,
                               coalesce(sum(f.unknown_count), 0) unknown_count
                        """
                                + grouped
                                + " order by "
                                + stableOrder(
                                        orderBy(sort, FAILURE_SORT, "count", "desc"), "error_code")
                                + " limit :limit offset :offset",
                        paged,
                        (rs, rowNum) ->
                                new CommunicationAnalyticsQueryService.CommunicationFailureItem(
                                        rs.getString("error_code"),
                                        rs.getLong("failure_count"),
                                        rs.getLong("retryable_count"),
                                        rs.getLong("permanent_count"),
                                        rs.getLong("unknown_count")));
        return new ProjectionPage<>(items, total == null ? 0 : total);
    }

    private Query campaignQuery(
            UUID tenantId, Instant from, Instant to, UUID campaignId, String channel, UUID userId) {
        LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate toDate = to.atZone(ZoneOffset.UTC).toLocalDate();
        if (!to.atZone(ZoneOffset.UTC).toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            toDate = toDate.plusDays(1);
        }

        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("fromDate", fromDate)
                        .addValue("toDate", toDate);
        StringBuilder where =
                new StringBuilder(
                        " where x.tenant_id = :tenantId"
                                + " and x.business_date >= :fromDate"
                                + " and x.business_date < :toDate");
        if (campaignId != null) {
            params.addValue("campaignId", campaignId);
            where.append(" and x.campaign_id = :campaignId");
        }
        if (channel != null && !channel.isBlank()) {
            params.addValue("channel", channel.trim().toUpperCase());
            where.append(" and x.channel = :channel");
        }
        if (userId != null) {
            params.addValue("userId", userId);
            where.append(" and x.created_by_user_id = :userId");
        }
        return new Query(where.toString(), params);
    }

    private record Query(String where, MapSqlParameterSource params) {}

    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource copy = new MapSqlParameterSource();
        for (String name : source.getParameterNames()) {
            copy.addValue(name, source.getValue(name));
        }
        return copy;
    }

    private String stableOrder(String order, String tieBreak) {
        return order + ", " + tieBreak + " asc nulls last";
    }

    private String orderBy(
            String sort,
            Map<String, String> allowed,
            String defaultField,
            String defaultDirection) {
        String value =
                sort == null || sort.isBlank()
                        ? defaultField + "," + defaultDirection
                        : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length > 2 || !allowed.containsKey(parts[0])) {
            throw new IllegalArgumentException("Unsupported report sort: " + value);
        }
        String direction = parts.length == 1 ? defaultDirection : parts[1].trim().toLowerCase();
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new IllegalArgumentException("Unsupported report sort direction: " + direction);
        }
        return allowed.get(parts[0]) + " " + direction + " nulls last";
    }

    public record ProjectionPage<T>(List<T> items, long totalElements) {}

    private BigDecimal rate(long numerator, long otherTerminal) {
        long denominator = numerator + otherTerminal;
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
