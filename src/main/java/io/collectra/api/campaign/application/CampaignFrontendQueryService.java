package io.collectra.api.campaign.application;

import io.collectra.api.shared.error.InvalidRequestException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignFrontendQueryService {
    public static final int MAX_SIZE = 200;
    private static final Set<String> CAMPAIGN_SORTS =
            Set.of("createdAt", "updatedAt", "name", "scheduledAt");

    private final NamedParameterJdbcTemplate jdbc;

    public CampaignFrontendQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignItem> campaigns(
            UUID tenantId,
            String search,
            String status,
            String channel,
            Instant scheduledFrom,
            Instant scheduledTo,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        validateRange(scheduledFrom, scheduledTo, "scheduledFrom", "scheduledTo");
        validateRange(createdFrom, createdTo, "createdFrom", "createdTo");
        Map<String, Object> params = base(tenantId, page, size);
        StringBuilder where = new StringBuilder(" WHERE tenant_id = :tenantId");
        if (search != null && !search.isBlank()) {
            where.append(" AND LOWER(name) LIKE :search");
            params.put("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        appendText(where, params, "status", status);
        appendText(where, params, "channel", channel);
        appendRange(where, params, "scheduled_at", "scheduledFrom", scheduledFrom, "scheduledTo", scheduledTo);
        appendRange(where, params, "created_at", "createdFrom", createdFrom, "createdTo", createdTo);
        long total = count("SELECT COUNT(*) FROM campaigns" + where, params);
        String order = orderBy(sort, CAMPAIGN_SORTS, "createdAt", "DESC");
        List<CampaignItem> items =
                jdbc.query(
                        """
                        SELECT id, name, status, template_version_id, channel, scheduled_at,
                               created_at, updated_at, version
                        FROM campaigns
                        """
                                + where
                                + order
                                + " LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) ->
                                new CampaignItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("name"),
                                        rs.getString("status"),
                                        rs.getObject("template_version_id", UUID.class),
                                        rs.getString("channel"),
                                        instant(rs, "scheduled_at"),
                                        instant(rs, "created_at"),
                                        instant(rs, "updated_at"),
                                        rs.getLong("version")));
        return page(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<RunItem> runs(
            UUID tenantId, UUID campaignId, String status, int page, int size) {
        validatePage(page, size);
        requireCampaign(tenantId, campaignId);
        Map<String, Object> params = base(tenantId, page, size);
        params.put("campaignId", campaignId);
        StringBuilder where =
                new StringBuilder(" WHERE tenant_id = :tenantId AND campaign_id = :campaignId");
        appendText(where, params, "status", status);
        long total = count("SELECT COUNT(*) FROM campaign_runs" + where, params);
        List<RunItem> items =
                jdbc.query(
                        """
                        SELECT id, campaign_id, status, recipient_count, sent_count, failed_count,
                               skipped_count, retry_count, prepared_at, started_at, completed_at,
                               created_at, updated_at, version
                        FROM campaign_runs
                        """
                                + where
                                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) -> {
                            int recipientCount = rs.getInt("recipient_count");
                            int sent = rs.getInt("sent_count");
                            int failed = rs.getInt("failed_count");
                            int skipped = rs.getInt("skipped_count");
                            return new RunItem(
                                    rs.getObject("id", UUID.class),
                                    rs.getObject("campaign_id", UUID.class),
                                    rs.getString("status"),
                                    recipientCount,
                                    sent,
                                    failed,
                                    skipped,
                                    rs.getInt("retry_count"),
                                    Math.max(0, recipientCount - sent - failed - skipped),
                                    instant(rs, "prepared_at"),
                                    instant(rs, "started_at"),
                                    instant(rs, "completed_at"),
                                    instant(rs, "created_at"),
                                    instant(rs, "updated_at"),
                                    rs.getLong("version"));
                        });
        return page(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<RecipientItem> recipients(
            UUID tenantId,
            UUID campaignId,
            UUID runId,
            String status,
            String channel,
            UUID customerId,
            int page,
            int size) {
        validatePage(page, size);
        requireCampaign(tenantId, campaignId);
        requireRun(tenantId, campaignId, runId);
        Map<String, Object> params = base(tenantId, page, size);
        params.put("campaignId", campaignId);
        params.put("runId", runId);
        StringBuilder where =
                new StringBuilder(
                        " WHERE tenant_id = :tenantId AND campaign_id = :campaignId AND run_id = :runId");
        appendText(where, params, "status", status);
        appendText(where, params, "channel", channel);
        if (customerId != null) {
            where.append(" AND customer_id = :customerId");
            params.put("customerId", customerId);
        }
        long total = count("SELECT COUNT(*) FROM campaign_recipients" + where, params);
        List<RecipientItem> items =
                jdbc.query(
                        """
                        SELECT id, customer_id, invoice_id, channel, destination, locale, status,
                               skip_reason, created_at, updated_at, version
                        FROM campaign_recipients
                        """
                                + where
                                + " ORDER BY created_at ASC, id ASC LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) ->
                                new RecipientItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("customer_id", UUID.class),
                                        rs.getObject("invoice_id", UUID.class),
                                        rs.getString("channel"),
                                        mask(rs.getString("destination")),
                                        rs.getString("locale"),
                                        rs.getString("status"),
                                        rs.getString("skip_reason"),
                                        instant(rs, "created_at"),
                                        instant(rs, "updated_at"),
                                        rs.getLong("version")));
        return page(items, page, size, total);
    }

    private void requireCampaign(UUID tenantId, UUID campaignId) {
        long count =
                count(
                        "SELECT COUNT(*) FROM campaigns WHERE tenant_id = :tenantId AND id = :id",
                        Map.of("tenantId", tenantId, "id", campaignId));
        if (count == 0) {
            throw new NoSuchElementException("Campaign not found");
        }
    }

    private void requireRun(UUID tenantId, UUID campaignId, UUID runId) {
        long count =
                count(
                        "SELECT COUNT(*) FROM campaign_runs WHERE tenant_id = :tenantId AND campaign_id = :campaignId AND id = :runId",
                        Map.of("tenantId", tenantId, "campaignId", campaignId, "runId", runId));
        if (count == 0) {
            throw new NoSuchElementException("Campaign run not found");
        }
    }

    private static void appendText(
            StringBuilder where, Map<String, Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = :").append(column);
            params.put(column, value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private static void appendRange(
            StringBuilder where,
            Map<String, Object> params,
            String column,
            String fromName,
            Instant from,
            String toName,
            Instant to) {
        if (from != null) {
            where.append(" AND ").append(column).append(" >= :").append(fromName);
            params.put(fromName, from);
        }
        if (to != null) {
            where.append(" AND ").append(column).append(" <= :").append(toName);
            params.put(toName, to);
        }
    }

    private static String orderBy(
            String sort, Set<String> allowed, String defaultField, String defaultDirection) {
        String field = defaultField;
        String direction = defaultDirection;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !allowed.contains(parts[0].trim())) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort field");
            }
            field = parts[0].trim();
            direction = parts[1].trim().toUpperCase(Locale.ROOT);
            if (!direction.equals("ASC") && !direction.equals("DESC")) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort direction");
            }
        }
        String column =
                switch (field) {
                    case "updatedAt" -> "updated_at";
                    case "name" -> "name";
                    case "scheduledAt" -> "scheduled_at";
                    default -> "created_at";
                };
        return " ORDER BY " + column + " " + direction + ", id " + direction;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
    }

    private static void validateRange(Instant from, Instant to, String fromName, String toName) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "INVALID_RANGE", fromName + " must not be after " + toName);
        }
    }

    private Map<String, Object> base(UUID tenantId, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("limit", size);
        params.put("offset", (long) page * size);
        return params;
    }

    private long count(String sql, Map<String, Object> params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String mask(String destination) {
        if (destination == null || destination.isBlank()) {
            return destination;
        }
        int at = destination.indexOf('@');
        if (at > 1) {
            return destination.substring(0, 1) + "***" + destination.substring(at);
        }
        String digits = destination.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return "***" + digits.substring(digits.length() - 4);
        }
        return "***";
    }

    private static <T> PageResponse<T> page(List<T> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, page, size, total, totalPages, page + 1 < totalPages);
    }

    public record PageResponse<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    public record CampaignItem(
            UUID id,
            String name,
            String status,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    public record RunItem(
            UUID id,
            UUID campaignId,
            String status,
            int recipientCount,
            int sentCount,
            int failedCount,
            int skippedCount,
            int retryCount,
            int pendingCount,
            Instant preparedAt,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    public record RecipientItem(
            UUID id,
            UUID customerId,
            UUID invoiceId,
            String channel,
            String destination,
            String locale,
            String status,
            String skipReason,
            Instant createdAt,
            Instant updatedAt,
            long version) {}
}
