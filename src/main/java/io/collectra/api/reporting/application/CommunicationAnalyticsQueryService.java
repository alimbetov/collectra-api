package io.collectra.api.reporting.application;

import io.collectra.api.shared.error.InvalidRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationAnalyticsQueryService {
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);
    private static final Duration MAX_WINDOW = Duration.ofDays(90);
    private static final int MAX_BUCKETS = 2160;

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

    private static final Map<String, String> TENANT_SORT =
            Map.of(
                    "sent", "sent_count",
                    "failed", "failed_count",
                    "recipients", "recipient_count",
                    "name", "tenant_name",
                    "slug", "tenant_slug");

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId businessZone;
    private final CommunicationProjectionQueryService projections;
    private final CommunicationProjectionProperties projectionProperties;

    public CommunicationAnalyticsQueryService(
            NamedParameterJdbcTemplate jdbc,
            Clock clock,
            ZoneId businessZone,
            CommunicationProjectionQueryService projections,
            CommunicationProjectionProperties projectionProperties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.businessZone = businessZone;
        this.projections = projections;
        this.projectionProperties = projectionProperties;
    }

    @Transactional(readOnly = true)
    public CommunicationSummary summary(Scope scope, Filter filter) {
        ResolvedFilter resolved = resolve(scope, filter, false);
        BusinessTotals business;
        MessageStates states;

        HybridRange hybrid = hybridRange(resolved);
        if (hybrid != null) {
            business =
                    projections.businessTotals(
                            resolved.tenantId(),
                            hybrid.historicalFrom(),
                            hybrid.historicalTo(),
                            resolved.campaignId(),
                            resolved.channel(),
                            resolved.userId());
            states =
                    projections.messageStates(
                            resolved.tenantId(),
                            hybrid.historicalFrom(),
                            hybrid.historicalTo(),
                            resolved.campaignId(),
                            resolved.channel(),
                            resolved.userId());

            if (hybrid.leading() != null) {
                ResolvedFilter leading =
                        withRange(
                                resolved,
                                hybrid.leading().from(),
                                hybrid.leading().to());
                business = add(business, businessTotals(leading));
                states = add(states, messageStates(leading));
            }
            if (hybrid.trailing() != null) {
                ResolvedFilter trailing =
                        withRange(
                                resolved,
                                hybrid.trailing().from(),
                                hybrid.trailing().to());
                business = add(business, businessTotals(trailing));
                states = add(states, messageStates(trailing));
            }
        } else {
            business = businessTotals(resolved);
            states = messageStates(resolved);
        }

        DocumentTotals documents = documentTotals(resolved);
        return new CommunicationSummary(
                resolved.generatedAt(),
                resolved.from(),
                resolved.to(),
                business,
                states,
                rate(business.sent(), business.failed()),
                rate(business.failed(), business.sent()),
                documents);
    }

    @Transactional(readOnly = true)
    public CommunicationTimeSeries timeseries(Scope scope, Filter filter, Bucket bucket) {
        ResolvedFilter resolved = resolve(scope, filter, true);
        validateBucketCount(resolved, bucket);

        String runWhere = where("cr", "c", resolved, true);
        String messageWhere = where("m", "c", resolved, true);
        String runBucket = bucketExpression("cr.created_at", bucket);
        String messageBucket = bucketExpression("m.created_at", bucket);

        String sql =
                """
                with run_points as (
                    select %s as bucket,
                           coalesce(sum(cr.recipient_count), 0) recipient_count,
                           coalesce(sum(cr.sent_count), 0) sent_count,
                           coalesce(sum(cr.failed_count), 0) failed_count,
                           coalesce(sum(cr.skipped_count), 0) skipped_count,
                           coalesce(sum(cr.retry_count), 0) retry_count
                      from campaign_runs cr
                      join campaigns c on c.id = cr.campaign_id
                     %s
                     group by 1
                ),
                message_points as (
                    select %s as bucket,
                           count(*) filter (where m.status = 'QUEUED') queued_count,
                           count(*) filter (where m.status = 'PROCESSING') processing_count,
                           count(*) filter (where m.status = 'RETRY_WAIT') retry_wait_count,
                           count(*) filter (where m.status = 'SENT') message_sent_count,
                           count(*) filter (where m.status = 'FAILED') message_failed_count,
                           count(*) filter (where m.status = 'UNKNOWN') unknown_count
                      from messages m
                      join campaigns c on c.id = m.campaign_id
                     %s
                     group by 1
                )
                select coalesce(r.bucket, m.bucket) bucket,
                       coalesce(r.recipient_count, 0) recipient_count,
                       coalesce(r.sent_count, 0) sent_count,
                       coalesce(r.failed_count, 0) failed_count,
                       coalesce(r.skipped_count, 0) skipped_count,
                       coalesce(r.retry_count, 0) retry_count,
                       coalesce(m.queued_count, 0) queued_count,
                       coalesce(m.processing_count, 0) processing_count,
                       coalesce(m.retry_wait_count, 0) retry_wait_count,
                       coalesce(m.message_sent_count, 0) message_sent_count,
                       coalesce(m.message_failed_count, 0) message_failed_count,
                       coalesce(m.unknown_count, 0) unknown_count
                  from run_points r
                  full outer join message_points m on m.bucket = r.bucket
                 order by bucket
                """
                        .formatted(runBucket, runWhere, messageBucket, messageWhere);

        List<CommunicationTimePoint> points =
                jdbc.query(
                        sql,
                        resolved.params(),
                        (rs, rowNum) ->
                                new CommunicationTimePoint(
                                        instant(rs, "bucket"),
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
        return new CommunicationTimeSeries(
                resolved.generatedAt(), resolved.from(), resolved.to(), bucket, points);
    }

    @Transactional(readOnly = true)
    public CommunicationChannelReport channels(Scope scope, Filter filter) {
        ResolvedFilter resolved = resolve(scope, filter, false);
        List<CommunicationChannelItem> items;

        HybridRange hybrid = hybridRange(resolved);
        if (hybrid != null) {
            items =
                    projections.channels(
                            resolved.tenantId(),
                            hybrid.historicalFrom(),
                            hybrid.historicalTo(),
                            resolved.campaignId(),
                            resolved.channel(),
                            resolved.userId());
            if (hybrid.leading() != null) {
                items =
                        mergeChannels(
                                items,
                                rawChannels(
                                        withRange(
                                                resolved,
                                                hybrid.leading().from(),
                                                hybrid.leading().to())));
            }
            if (hybrid.trailing() != null) {
                items =
                        mergeChannels(
                                items,
                                rawChannels(
                                        withRange(
                                                resolved,
                                                hybrid.trailing().from(),
                                                hybrid.trailing().to())));
            }
        } else {
            items = rawChannels(resolved);
        }

        return new CommunicationChannelReport(
                resolved.generatedAt(), resolved.from(), resolved.to(), items);
    }

    private List<CommunicationChannelItem> rawChannels(ResolvedFilter resolved) {
        String runWhere = where("cr", "c", resolved, true);
        String messageWhere = where("m", "c", resolved, true);

        String sql =
                """
                with run_channels as (
                    select c.channel,
                           coalesce(sum(cr.recipient_count), 0) recipient_count,
                           coalesce(sum(cr.sent_count), 0) sent_count,
                           coalesce(sum(cr.failed_count), 0) failed_count,
                           coalesce(sum(cr.skipped_count), 0) skipped_count,
                           coalesce(sum(cr.retry_count), 0) retry_count
                      from campaign_runs cr
                      join campaigns c on c.id = cr.campaign_id
                     %s
                     group by c.channel
                ),
                message_channels as (
                    select m.channel,
                           count(*) message_count,
                           count(*) filter (where m.status = 'SENT') message_sent_count,
                           count(*) filter (where m.status = 'FAILED') message_failed_count,
                           count(*) filter (where m.status = 'RETRY_WAIT') retry_wait_count,
                           count(*) filter (where m.status = 'UNKNOWN') unknown_count
                      from messages m
                      join campaigns c on c.id = m.campaign_id
                     %s
                     group by m.channel
                )
                select coalesce(r.channel, m.channel) channel,
                       coalesce(m.message_count, 0) message_count,
                       coalesce(r.recipient_count, 0) recipient_count,
                       coalesce(r.sent_count, 0) sent_count,
                       coalesce(r.failed_count, 0) failed_count,
                       coalesce(r.skipped_count, 0) skipped_count,
                       coalesce(r.retry_count, 0) retry_count,
                       coalesce(m.retry_wait_count, 0) retry_wait_count,
                       coalesce(m.unknown_count, 0) unknown_count
                  from run_channels r
                  full outer join message_channels m on m.channel = r.channel
                 order by channel
                """
                        .formatted(runWhere, messageWhere);

        return jdbc.query(
                sql,
                resolved.params(),
                (rs, rowNum) -> {
                    long sent = rs.getLong("sent_count");
                    long failed = rs.getLong("failed_count");
                    return new CommunicationChannelItem(
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
    public CommunicationPageReport<CommunicationUserItem> users(
            Scope scope, Filter filter, int page, int size, String sort) {
        validatePage(page, size);
        ResolvedFilter resolved = resolve(scope, filter, false);
        String runWhere = where("cr", "c", resolved, true);
        String messageWhere = where("m", "c", resolved, true);
        String order = orderBy(sort, USER_SORT, "sent", "desc");

        String grouped =
                """
                with run_users as (
                    select cr.tenant_id,
                           c.created_by user_id,
                           count(distinct c.id) campaign_count,
                           coalesce(sum(cr.recipient_count), 0) recipient_count,
                           coalesce(sum(cr.sent_count), 0) sent_count,
                           coalesce(sum(cr.failed_count), 0) failed_count,
                           coalesce(sum(cr.skipped_count), 0) skipped_count,
                           coalesce(sum(cr.retry_count), 0) retry_count
                      from campaign_runs cr
                      join campaigns c on c.id = cr.campaign_id
                     %s
                     group by cr.tenant_id, c.created_by
                ),
                message_users as (
                    select m.tenant_id,
                           c.created_by user_id,
                           count(*) filter (where m.status = 'UNKNOWN') unknown_count
                      from messages m
                      join campaigns c on c.id = m.campaign_id
                     %s
                     group by m.tenant_id, c.created_by
                ),
                combined as (
                    select coalesce(r.tenant_id, m.tenant_id) tenant_id,
                           coalesce(r.user_id, m.user_id) user_id,
                           coalesce(r.campaign_count, 0) campaign_count,
                           coalesce(r.recipient_count, 0) recipient_count,
                           coalesce(r.sent_count, 0) sent_count,
                           coalesce(r.failed_count, 0) failed_count,
                           coalesce(r.skipped_count, 0) skipped_count,
                           coalesce(r.retry_count, 0) retry_count,
                           coalesce(m.unknown_count, 0) unknown_count
                      from run_users r
                      full outer join message_users m
                        on m.tenant_id = r.tenant_id
                       and m.user_id is not distinct from r.user_id
                )
                """
                        .formatted(runWhere, messageWhere);

        long total =
                jdbc.queryForObject(
                        grouped + " select count(*) from combined", resolved.params(), Long.class);

        MapSqlParameterSource params =
                copy(resolved.params())
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationUserItem> items =
                jdbc.query(
                        grouped
                                + """
                                select x.tenant_id,
                                       t.name tenant_name,
                                       x.user_id,
                                       coalesce(ua.email, 'SYSTEM') email,
                                       coalesce(ua.display_name, 'SYSTEM') display_name,
                                       x.campaign_count,
                                       x.recipient_count,
                                       x.sent_count,
                                       x.failed_count,
                                       x.skipped_count,
                                       x.retry_count,
                                       x.unknown_count
                                  from combined x
                                  join tenants t on t.id = x.tenant_id
                                  left join user_accounts ua on ua.id = x.user_id
                                 order by
                                """
                                + order
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) -> {
                            long sent = rs.getLong("sent_count");
                            long failed = rs.getLong("failed_count");
                            return new CommunicationUserItem(
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

        return page(
                resolved.generatedAt(), resolved.from(), resolved.to(), items, page, size, total);
    }

    @Transactional(readOnly = true)
    public CommunicationPageReport<CommunicationCampaignItem> campaigns(
            Scope scope, Filter filter, int page, int size, String sort) {
        validatePage(page, size);
        ResolvedFilter resolved = resolve(scope, filter, false);
        String where = where("cr", "c", resolved, true);
        String order = orderBy(sort, CAMPAIGN_SORT, "lastRunAt", "desc");

        String grouped =
                """
                from campaign_runs cr
                join campaigns c on c.id = cr.campaign_id
                join tenants t on t.id = cr.tenant_id
                left join user_accounts ua on ua.id = c.created_by
                %s
                group by c.id, c.name, c.created_by, c.channel, cr.tenant_id, t.name, ua.email, ua.display_name
                """
                        .formatted(where);

        long total =
                jdbc.queryForObject(
                        "select count(*) from (select c.id " + grouped + ") x",
                        resolved.params(),
                        Long.class);

        MapSqlParameterSource params =
                copy(resolved.params())
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationCampaignItem> items =
                jdbc.query(
                        """
                        select c.id campaign_id,
                               c.name campaign_name,
                               cr.tenant_id,
                               t.name tenant_name,
                               c.created_by,
                               coalesce(ua.email, 'SYSTEM') created_by_email,
                               c.channel,
                               count(*) run_count,
                               coalesce(sum(cr.recipient_count), 0) recipient_count,
                               coalesce(sum(cr.sent_count), 0) sent_count,
                               coalesce(sum(cr.failed_count), 0) failed_count,
                               coalesce(sum(cr.skipped_count), 0) skipped_count,
                               coalesce(sum(cr.retry_count), 0) retry_count,
                               max(cr.created_at) last_run_at
                        """
                                + grouped
                                + " order by "
                                + order
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) -> {
                            long sent = rs.getLong("sent_count");
                            long failed = rs.getLong("failed_count");
                            return new CommunicationCampaignItem(
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
                                    instant(rs, "last_run_at"),
                                    rate(sent, failed));
                        });

        return page(
                resolved.generatedAt(), resolved.from(), resolved.to(), items, page, size, total);
    }

    @Transactional(readOnly = true)
    public CommunicationPageReport<CommunicationFailureItem> failures(
            Scope scope, Filter filter, int page, int size, String sort) {
        validatePage(page, size);
        ResolvedFilter resolved = resolve(scope, filter, false);
        String where = attemptWhere(resolved);
        String order = orderBy(sort, FAILURE_SORT, "count", "desc");

        String grouped =
                """
                from message_delivery_attempts a
                join messages m on m.id = a.message_id
                join campaigns c on c.id = m.campaign_id
                %s
                group by coalesce(a.error_code, 'UNCLASSIFIED')
                """
                        .formatted(where);

        long total =
                jdbc.queryForObject(
                        "select count(*) from (select coalesce(a.error_code, 'UNCLASSIFIED') "
                                + grouped
                                + ") x",
                        resolved.params(),
                        Long.class);

        MapSqlParameterSource params =
                copy(resolved.params())
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationFailureItem> items =
                jdbc.query(
                        """
                        select coalesce(a.error_code, 'UNCLASSIFIED') error_code,
                               count(*) failure_count,
                               count(*) filter (where a.status = 'RETRYABLE_FAILURE') retryable_count,
                               count(*) filter (where a.status = 'PERMANENT_FAILURE') permanent_count,
                               count(*) filter (where a.status = 'UNKNOWN') unknown_count
                        """
                                + grouped
                                + " order by "
                                + order
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) ->
                                new CommunicationFailureItem(
                                        rs.getString("error_code"),
                                        rs.getLong("failure_count"),
                                        rs.getLong("retryable_count"),
                                        rs.getLong("permanent_count"),
                                        rs.getLong("unknown_count")));

        return page(
                resolved.generatedAt(), resolved.from(), resolved.to(), items, page, size, total);
    }

    @Transactional(readOnly = true)
    public CommunicationDocumentReport documents(Scope scope, Filter filter) {
        ResolvedFilter resolved = resolve(scope, filter, false);
        DocumentTotals totals = documentTotals(resolved);
        return new CommunicationDocumentReport(
                resolved.generatedAt(), resolved.from(), resolved.to(), totals);
    }

    @Transactional(readOnly = true)
    public CommunicationPageReport<CommunicationTenantItem> tenants(
            Scope scope, Filter filter, int page, int size, String sort) {
        if (!scope.platformScope()) {
            throw new InvalidRequestException(
                    "INVALID_FILTER", "Tenant breakdown is available only in platform scope");
        }
        validatePage(page, size);
        ResolvedFilter resolved = resolve(scope, filter, false);
        String runWhere = where("cr", "c", resolved, true);
        String messageWhere = where("m", "c", resolved, true);
        String order = orderBy(sort, TENANT_SORT, "sent", "desc");

        String grouped =
                """
                with run_tenants as (
                    select cr.tenant_id,
                           count(distinct c.id) campaign_count,
                           coalesce(sum(cr.recipient_count), 0) recipient_count,
                           coalesce(sum(cr.sent_count), 0) sent_count,
                           coalesce(sum(cr.failed_count), 0) failed_count,
                           coalesce(sum(cr.skipped_count), 0) skipped_count,
                           coalesce(sum(cr.retry_count), 0) retry_count
                      from campaign_runs cr
                      join campaigns c on c.id = cr.campaign_id
                     %s
                     group by cr.tenant_id
                ),
                message_tenants as (
                    select m.tenant_id,
                           count(*) filter (where m.status = 'UNKNOWN') unknown_count
                      from messages m
                      join campaigns c on c.id = m.campaign_id
                     %s
                     group by m.tenant_id
                ),
                combined as (
                    select coalesce(r.tenant_id, m.tenant_id) tenant_id,
                           coalesce(r.campaign_count, 0) campaign_count,
                           coalesce(r.recipient_count, 0) recipient_count,
                           coalesce(r.sent_count, 0) sent_count,
                           coalesce(r.failed_count, 0) failed_count,
                           coalesce(r.skipped_count, 0) skipped_count,
                           coalesce(r.retry_count, 0) retry_count,
                           coalesce(m.unknown_count, 0) unknown_count
                      from run_tenants r
                      full outer join message_tenants m on m.tenant_id = r.tenant_id
                )
                """
                        .formatted(runWhere, messageWhere);

        long total =
                jdbc.queryForObject(
                        grouped + " select count(*) from combined", resolved.params(), Long.class);

        MapSqlParameterSource params =
                copy(resolved.params())
                        .addValue("limit", size)
                        .addValue("offset", Math.multiplyExact(page, size));

        List<CommunicationTenantItem> items =
                jdbc.query(
                        grouped
                                + """
                                select x.tenant_id,
                                       t.slug tenant_slug,
                                       t.name tenant_name,
                                       x.campaign_count,
                                       x.recipient_count,
                                       x.sent_count,
                                       x.failed_count,
                                       x.skipped_count,
                                       x.retry_count,
                                       x.unknown_count
                                  from combined x
                                  join tenants t on t.id = x.tenant_id
                                 order by
                                """
                                + order
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) -> {
                            long sent = rs.getLong("sent_count");
                            long failed = rs.getLong("failed_count");
                            return new CommunicationTenantItem(
                                    rs.getObject("tenant_id", UUID.class),
                                    rs.getString("tenant_slug"),
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

        return page(
                resolved.generatedAt(), resolved.from(), resolved.to(), items, page, size, total);
    }

    @Transactional(readOnly = true)
    public BusinessTotals deliveryTotalsAllTime(UUID tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
        return jdbc.queryForObject(
                """
                select coalesce(sum(recipient_count), 0) recipient_count,
                       coalesce(sum(sent_count), 0) sent_count,
                       coalesce(sum(failed_count), 0) failed_count,
                       coalesce(sum(skipped_count), 0) skipped_count,
                       coalesce(sum(retry_count), 0) retry_count
                  from campaign_runs
                 where tenant_id = :tenantId
                """,
                params,
                (rs, rowNum) -> business(rs));
    }

    private BusinessTotals businessTotals(ResolvedFilter resolved) {
        String where = where("cr", "c", resolved, true);
        return jdbc.queryForObject(
                """
                select coalesce(sum(cr.recipient_count), 0) recipient_count,
                       coalesce(sum(cr.sent_count), 0) sent_count,
                       coalesce(sum(cr.failed_count), 0) failed_count,
                       coalesce(sum(cr.skipped_count), 0) skipped_count,
                       coalesce(sum(cr.retry_count), 0) retry_count
                  from campaign_runs cr
                  join campaigns c on c.id = cr.campaign_id
                """
                        + where,
                resolved.params(),
                (rs, rowNum) -> business(rs));
    }

    private MessageStates messageStates(ResolvedFilter resolved) {
        String where = where("m", "c", resolved, true);
        return jdbc.queryForObject(
                """
                select count(*) filter (where m.status = 'QUEUED') queued_count,
                       count(*) filter (where m.status = 'PROCESSING') processing_count,
                       count(*) filter (where m.status = 'RETRY_WAIT') retry_wait_count,
                       count(*) filter (where m.status = 'SENT') sent_count,
                       count(*) filter (where m.status = 'FAILED') failed_count,
                       count(*) filter (where m.status = 'UNKNOWN') unknown_count
                  from messages m
                  join campaigns c on c.id = m.campaign_id
                """
                        + where,
                resolved.params(),
                (rs, rowNum) ->
                        new MessageStates(
                                rs.getLong("queued_count"),
                                rs.getLong("processing_count"),
                                rs.getLong("retry_wait_count"),
                                rs.getLong("sent_count"),
                                rs.getLong("failed_count"),
                                rs.getLong("unknown_count")));
    }

    private DocumentTotals documentTotals(ResolvedFilter resolved) {
        String where = documentWhere(resolved);
        return jdbc.queryForObject(
                """
                select count(*) created_count,
                       count(*) filter (
                           where l.status = 'PENDING' and l.expires_at > :generatedAt
                       ) pending_count,
                       count(*) filter (
                           where l.status = 'READY' and l.expires_at > :generatedAt
                       ) ready_count,
                       count(*) filter (where l.status = 'FAILED') failed_count,
                       count(*) filter (where l.expires_at <= :generatedAt) expired_count,
                       coalesce(sum(l.access_count), 0) access_count,
                       count(*) filter (where l.access_count > 0) accessed_link_count
                  from message_document_links l
                  join messages m on m.id = l.message_id
                  join campaigns c on c.id = m.campaign_id
                """
                        + where,
                resolved.params(),
                (rs, rowNum) ->
                        new DocumentTotals(
                                rs.getLong("created_count"),
                                rs.getLong("pending_count"),
                                rs.getLong("ready_count"),
                                rs.getLong("failed_count"),
                                rs.getLong("expired_count"),
                                rs.getLong("access_count"),
                                rs.getLong("accessed_link_count")));
    }

    private ResolvedFilter resolve(Scope scope, Filter filter, boolean detailed) {
        Instant generatedAt = clock.instant();
        Instant to = filter.to() == null ? generatedAt : filter.to();
        Instant from = filter.from() == null ? to.minus(DEFAULT_WINDOW) : filter.from();

        if (!from.isBefore(to)) {
            throw new InvalidRequestException("INVALID_FILTER", "from must be before to");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new InvalidRequestException(
                    "REPORT_RANGE_TOO_LARGE", "Communication report range must not exceed 90 days");
        }
        if (filter.channel() != null && !isChannel(filter.channel())) {
            throw new InvalidRequestException(
                    "INVALID_FILTER", "Unsupported communication channel: " + filter.channel());
        }
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("generatedAt", java.sql.Timestamp.from(generatedAt))
                        .addValue("from", java.sql.Timestamp.from(from))
                        .addValue("to", java.sql.Timestamp.from(to));
        if (scope.tenantId() != null) params.addValue("tenantId", scope.tenantId());
        UUID effectiveUser = scope.userId() != null ? scope.userId() : filter.userId();
        if (effectiveUser != null) params.addValue("userId", effectiveUser);
        if (filter.campaignId() != null) params.addValue("campaignId", filter.campaignId());
        if (filter.runId() != null) params.addValue("runId", filter.runId());
        if (filter.channel() != null)
            params.addValue("channel", filter.channel().trim().toUpperCase());

        return new ResolvedFilter(
                generatedAt,
                from,
                to,
                scope.tenantId(),
                effectiveUser,
                filter.campaignId(),
                filter.runId(),
                filter.channel() == null ? null : filter.channel().trim().toUpperCase(),
                params);
    }

    private String where(
            String factAlias, String campaignAlias, ResolvedFilter filter, boolean hasRunId) {
        StringBuilder where =
                new StringBuilder(
                        " where "
                                + factAlias
                                + ".created_at >= :from and "
                                + factAlias
                                + ".created_at < :to ");
        appendScope(where, factAlias, campaignAlias, filter, hasRunId);
        return where.toString();
    }

    private String attemptWhere(ResolvedFilter filter) {
        StringBuilder where =
                new StringBuilder(
                        " where a.completed_at >= :from and a.completed_at < :to "
                                + "and a.status in ('RETRYABLE_FAILURE','PERMANENT_FAILURE','UNKNOWN') ");
        appendScope(where, "m", "c", filter, true);
        return where.toString();
    }

    private String documentWhere(ResolvedFilter filter) {
        StringBuilder where =
                new StringBuilder(" where l.created_at >= :from and l.created_at < :to ");
        appendScope(where, "m", "c", filter, true);
        return where.toString();
    }

    private void appendScope(
            StringBuilder where,
            String factAlias,
            String campaignAlias,
            ResolvedFilter filter,
            boolean hasRunId) {
        if (filter.tenantId() != null) {
            where.append(" and ").append(factAlias).append(".tenant_id = :tenantId ");
        }
        if (filter.userId() != null) {
            where.append(" and ").append(campaignAlias).append(".created_by = :userId ");
        }
        if (filter.campaignId() != null) {
            where.append(" and ").append(campaignAlias).append(".id = :campaignId ");
        }
        if (filter.runId() != null) {
            if (!hasRunId) {
                throw new InvalidRequestException(
                        "INVALID_FILTER", "runId is not supported for this report");
            }
            String column =
                    "cr".equals(factAlias) ? factAlias + ".id" : factAlias + ".campaign_run_id";
            where.append(" and ").append(column).append(" = :runId ");
        }
        if (filter.channel() != null) {
            String column =
                    "m".equals(factAlias) ? factAlias + ".channel" : campaignAlias + ".channel";
            where.append(" and ").append(column).append(" = :channel ");
        }
    }

    private String bucketExpression(String column, Bucket bucket) {
        String unit =
                switch (bucket) {
                    case HOUR -> "hour";
                    case DAY -> "day";
                    case WEEK -> "week";
                };
        return "date_trunc('" + unit + "', " + column + " at time zone 'UTC') at time zone 'UTC'";
    }

    private void validateBucketCount(ResolvedFilter filter, Bucket bucket) {
        long seconds = Duration.between(filter.from(), filter.to()).getSeconds();
        long bucketSeconds =
                switch (bucket) {
                    case HOUR -> 3600;
                    case DAY -> 86400;
                    case WEEK -> 604800;
                };
        long buckets = (seconds + bucketSeconds - 1) / bucketSeconds;
        if (buckets > MAX_BUCKETS) {
            throw new InvalidRequestException(
                    "REPORT_RANGE_TOO_LARGE", "Requested time series contains too many buckets");
        }
    }

    private HybridRange hybridRange(ResolvedFilter resolved) {
        if (!projectionProperties.isEnabled()
                || resolved.tenantId() == null
                || resolved.runId() != null) {
            return null;
        }

        Instant todayStart =
                java.time.LocalDate.now(clock.withZone(businessZone))
                        .atStartOfDay(businessZone)
                        .toInstant();
        Instant historicalLimit =
                resolved.to().isBefore(todayStart) ? resolved.to() : todayStart;

        Instant historicalFrom = ceilBusinessDay(resolved.from());
        Instant historicalTo = floorBusinessDay(historicalLimit);

        if (!historicalFrom.isBefore(historicalTo)) {
            return null;
        }
        if (!projections.coversTenantRange(
                resolved.tenantId(), historicalFrom, historicalTo)) {
            return null;
        }

        Range leading =
                resolved.from().isBefore(historicalFrom)
                        ? new Range(resolved.from(), historicalFrom)
                        : null;
        Range trailing =
                historicalTo.isBefore(resolved.to())
                        ? new Range(historicalTo, resolved.to())
                        : null;
        return new HybridRange(historicalFrom, historicalTo, leading, trailing);
    }

    private Instant floorBusinessDay(Instant value) {
        return value.atZone(businessZone).toLocalDate().atStartOfDay(businessZone).toInstant();
    }

    private Instant ceilBusinessDay(Instant value) {
        Instant floor = floorBusinessDay(value);
        if (floor.equals(value)) {
            return value;
        }
        return value.atZone(businessZone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(businessZone)
                .toInstant();
    }

    private ResolvedFilter withRange(ResolvedFilter source, Instant from, Instant to) {
        MapSqlParameterSource params = copy(source.params());
        params.addValue("from", java.sql.Timestamp.from(from));
        params.addValue("to", java.sql.Timestamp.from(to));
        return new ResolvedFilter(
                source.generatedAt(),
                from,
                to,
                source.tenantId(),
                source.userId(),
                source.campaignId(),
                source.runId(),
                source.channel(),
                params);
    }

    private BusinessTotals add(BusinessTotals left, BusinessTotals right) {
        return new BusinessTotals(
                left.recipients() + right.recipients(),
                left.sent() + right.sent(),
                left.failed() + right.failed(),
                left.skipped() + right.skipped(),
                left.retries() + right.retries());
    }

    private MessageStates add(MessageStates left, MessageStates right) {
        return new MessageStates(
                left.queued() + right.queued(),
                left.processing() + right.processing(),
                left.retryWait() + right.retryWait(),
                left.sent() + right.sent(),
                left.failed() + right.failed(),
                left.unknown() + right.unknown());
    }

    private List<CommunicationChannelItem> mergeChannels(
            List<CommunicationChannelItem> historical, List<CommunicationChannelItem> live) {
        Map<String, ChannelAccumulator> merged = new LinkedHashMap<>();
        historical.forEach(item -> merged.computeIfAbsent(item.channel(), key -> new ChannelAccumulator()).add(item));
        live.forEach(item -> merged.computeIfAbsent(item.channel(), key -> new ChannelAccumulator()).add(item));

        return merged.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(
                        entry -> {
                            ChannelAccumulator value = entry.getValue();
                            return new CommunicationChannelItem(
                                    entry.getKey(),
                                    value.messageCount,
                                    value.recipients,
                                    value.sent,
                                    value.failed,
                                    value.skipped,
                                    value.retries,
                                    value.retryWaitCurrent,
                                    value.unknownCurrent,
                                    rate(value.sent, value.failed));
                        })
                .toList();
    }

    private static final class ChannelAccumulator {
        private long messageCount;
        private long recipients;
        private long sent;
        private long failed;
        private long skipped;
        private long retries;
        private long retryWaitCurrent;
        private long unknownCurrent;

        private void add(CommunicationChannelItem item) {
            messageCount += item.messageCount();
            recipients += item.recipients();
            sent += item.sent();
            failed += item.failed();
            skipped += item.skipped();
            retries += item.retries();
            retryWaitCurrent += item.retryWaitCurrent();
            unknownCurrent += item.unknownCurrent();
        }
    }

    private record Range(Instant from, Instant to) {}

    private record HybridRange(
            Instant historicalFrom, Instant historicalTo, Range leading, Range trailing) {}

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
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported report sort: " + value);
        }
        String direction = parts.length == 1 ? defaultDirection : parts[1].trim().toLowerCase();
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported report sort direction: " + direction);
        }
        return allowed.get(parts[0]) + " " + direction + " nulls last";
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_FILTER", "page must be >= 0 and size must be between 1 and 200");
        }
    }

    private boolean isChannel(String value) {
        return switch (value.trim().toUpperCase()) {
            case "EMAIL", "SMS", "WHATSAPP", "TELEGRAM", "IN_APP" -> true;
            default -> false;
        };
    }

    private BusinessTotals business(ResultSet rs) throws SQLException {
        return new BusinessTotals(
                rs.getLong("recipient_count"),
                rs.getLong("sent_count"),
                rs.getLong("failed_count"),
                rs.getLong("skipped_count"),
                rs.getLong("retry_count"));
    }

    private BigDecimal rate(long numerator, long otherTerminal) {
        long denominator = numerator + otherTerminal;
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private MapSqlParameterSource copy(MapSqlParameterSource source) {
        MapSqlParameterSource copy = new MapSqlParameterSource();
        for (String name : source.getParameterNames()) {
            copy.addValue(name, source.getValue(name));
        }
        return copy;
    }

    private <T> CommunicationPageReport<T> page(
            Instant generatedAt,
            Instant from,
            Instant to,
            List<T> items,
            int page,
            int size,
            long total) {
        return new CommunicationPageReport<>(
                generatedAt,
                from,
                to,
                items,
                page,
                size,
                total,
                total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    public enum Bucket {
        HOUR,
        DAY,
        WEEK
    }

    public record Scope(UUID tenantId, UUID userId, boolean platformScope) {
        public static Scope tenant(UUID tenantId) {
            return new Scope(tenantId, null, false);
        }

        public static Scope platform(UUID tenantId, UUID userId) {
            return new Scope(tenantId, userId, true);
        }
    }

    public record Filter(
            Instant from, Instant to, UUID campaignId, UUID runId, String channel, UUID userId) {}

    private record ResolvedFilter(
            Instant generatedAt,
            Instant from,
            Instant to,
            UUID tenantId,
            UUID userId,
            UUID campaignId,
            UUID runId,
            String channel,
            MapSqlParameterSource params) {}

    public record BusinessTotals(
            long recipients, long sent, long failed, long skipped, long retries) {}

    public record MessageStates(
            long queued, long processing, long retryWait, long sent, long failed, long unknown) {}

    public record DocumentTotals(
            long created,
            long pending,
            long ready,
            long failed,
            long expired,
            long accessCount,
            long accessedLinks) {}

    public record CommunicationSummary(
            Instant generatedAt,
            Instant from,
            Instant to,
            BusinessTotals business,
            MessageStates messages,
            BigDecimal terminalSuccessRate,
            BigDecimal terminalFailureRate,
            DocumentTotals documents) {}

    public record CommunicationTimePoint(
            Instant bucketStart,
            long recipients,
            long sent,
            long failed,
            long skipped,
            long retries,
            long queued,
            long processing,
            long retryWait,
            long messageSent,
            long messageFailed,
            long unknown) {}

    public record CommunicationTimeSeries(
            Instant generatedAt,
            Instant from,
            Instant to,
            Bucket bucket,
            List<CommunicationTimePoint> items) {}

    public record CommunicationChannelItem(
            String channel,
            long messageCount,
            long recipients,
            long sent,
            long failed,
            long skipped,
            long retries,
            long retryWaitCurrent,
            long unknownCurrent,
            BigDecimal terminalSuccessRate) {}

    public record CommunicationChannelReport(
            Instant generatedAt, Instant from, Instant to, List<CommunicationChannelItem> items) {}

    public record CommunicationUserItem(
            UUID userId,
            String email,
            String displayName,
            UUID tenantId,
            String tenantName,
            long campaignCount,
            long recipients,
            long sent,
            long failed,
            long skipped,
            long retries,
            long unknownCurrent,
            BigDecimal terminalSuccessRate) {}

    public record CommunicationCampaignItem(
            UUID campaignId,
            String name,
            UUID tenantId,
            String tenantName,
            UUID createdBy,
            String createdByEmail,
            String channel,
            long runs,
            long recipients,
            long sent,
            long failed,
            long skipped,
            long retries,
            Instant lastRunAt,
            BigDecimal terminalSuccessRate) {}

    public record CommunicationFailureItem(
            String errorCode, long count, long retryable, long permanent, long unknown) {}

    public record CommunicationTenantItem(
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            long campaignCount,
            long recipients,
            long sent,
            long failed,
            long skipped,
            long retries,
            long unknownCurrent,
            BigDecimal terminalSuccessRate) {}

    public record CommunicationDocumentReport(
            Instant generatedAt, Instant from, Instant to, DocumentTotals totals) {}

    public record CommunicationPageReport<T>(
            Instant generatedAt,
            Instant from,
            Instant to,
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {}
}
