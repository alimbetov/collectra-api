package io.collectra.api.template.application;

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
public class TemplateFrontendQueryService {
    public static final int MAX_SIZE = 200;
    private static final Set<String> TEMPLATE_SORTS = Set.of("createdAt", "updatedAt", "name", "code");

    private final NamedParameterJdbcTemplate jdbc;

    public TemplateFrontendQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateItem> templates(
            UUID tenantId,
            String search,
            String channel,
            String status,
            String locale,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        validateRange(createdFrom, createdTo);
        Map<String, Object> params = base(tenantId, page, size);
        StringBuilder where = new StringBuilder(" WHERE dt.tenant_id = :tenantId");
        if (search != null && !search.isBlank()) {
            where.append(" AND (LOWER(dt.name) LIKE :search OR LOWER(dt.code) LIKE :search)");
            params.put("search", "%" + search.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND dt.status = :status");
            params.put("status", status.trim().toUpperCase(Locale.ROOT));
        }
        if (createdFrom != null) {
            where.append(" AND dt.created_at >= :createdFrom");
            params.put("createdFrom", createdFrom);
        }
        if (createdTo != null) {
            where.append(" AND dt.created_at <= :createdTo");
            params.put("createdTo", createdTo);
        }
        if ((channel != null && !channel.isBlank()) || (locale != null && !locale.isBlank())) {
            where.append(" AND EXISTS (SELECT 1 FROM template_versions tv WHERE tv.template_id = dt.id");
            if (channel != null && !channel.isBlank()) {
                where.append(" AND tv.channel = :channel");
                params.put("channel", channel.trim().toUpperCase(Locale.ROOT));
            }
            if (locale != null && !locale.isBlank()) {
                where.append(" AND tv.locale = :locale");
                params.put("locale", locale.trim());
            }
            where.append(")");
        }
        long total = count("SELECT COUNT(*) FROM document_templates dt" + where, params);
        String order = order(sort);
        List<TemplateItem> items =
                jdbc.query(
                        """
                        SELECT dt.id, dt.code, dt.name, dt.document_type, dt.status,
                               dt.created_at, dt.updated_at, dt.version
                        FROM document_templates dt
                        """
                                + where
                                + order
                                + " LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) ->
                                new TemplateItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("code"),
                                        rs.getString("name"),
                                        rs.getString("document_type"),
                                        rs.getString("status"),
                                        instant(rs, "created_at"),
                                        instant(rs, "updated_at"),
                                        rs.getLong("version")));
        return page(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<VersionItem> versions(
            UUID tenantId,
            UUID templateId,
            String channel,
            String status,
            String locale,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        validatePage(page, size);
        validateRange(createdFrom, createdTo);
        requireTemplate(tenantId, templateId);
        Map<String, Object> params = base(tenantId, page, size);
        params.put("templateId", templateId);
        StringBuilder where = new StringBuilder(" WHERE tv.template_id = :templateId");
        if (channel != null && !channel.isBlank()) {
            where.append(" AND tv.channel = :channel");
            params.put("channel", channel.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND tv.status = :status");
            params.put("status", status.trim().toUpperCase(Locale.ROOT));
        }
        if (locale != null && !locale.isBlank()) {
            where.append(" AND tv.locale = :locale");
            params.put("locale", locale.trim());
        }
        if (createdFrom != null) {
            where.append(" AND tv.created_at >= :createdFrom");
            params.put("createdFrom", createdFrom);
        }
        if (createdTo != null) {
            where.append(" AND tv.created_at <= :createdTo");
            params.put("createdTo", createdTo);
        }
        long total = count("SELECT COUNT(*) FROM template_versions tv" + where, params);
        List<VersionItem> items =
                jdbc.query(
                        """
                        SELECT tv.id, tv.template_id, tv.template_version, tv.locale, tv.channel,
                               tv.subject, tv.status, tv.created_at, tv.updated_at, tv.version
                        FROM template_versions tv
                        """
                                + where
                                + " ORDER BY tv.template_version DESC, tv.locale ASC, tv.channel ASC, tv.id ASC"
                                + " LIMIT :limit OFFSET :offset",
                        params,
                        (rs, rowNum) ->
                                new VersionItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("template_id", UUID.class),
                                        rs.getInt("template_version"),
                                        rs.getString("locale"),
                                        rs.getString("channel"),
                                        rs.getString("subject"),
                                        rs.getString("status"),
                                        instant(rs, "created_at"),
                                        instant(rs, "updated_at"),
                                        rs.getLong("version")));
        return page(items, page, size, total);
    }

    private void requireTemplate(UUID tenantId, UUID templateId) {
        long found =
                count(
                        "SELECT COUNT(*) FROM document_templates WHERE tenant_id = :tenantId AND id = :id",
                        Map.of("tenantId", tenantId, "id", templateId));
        if (found == 0) {
            throw new NoSuchElementException("Template not found");
        }
    }

    private static String order(String sort) {
        String field = "createdAt";
        String direction = "DESC";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            if (parts.length != 2 || !TEMPLATE_SORTS.contains(parts[0].trim())) {
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
                    case "updatedAt" -> "dt.updated_at";
                    case "name" -> "dt.name";
                    case "code" -> "dt.code";
                    default -> "dt.created_at";
                };
        return " ORDER BY " + column + " " + direction + ", dt.id " + direction;
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

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
    }

    private static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "INVALID_RANGE", "createdFrom must not be after createdTo");
        }
    }

    private static Instant instant(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
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

    public record TemplateItem(
            UUID id,
            String code,
            String name,
            String documentType,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    public record VersionItem(
            UUID id,
            UUID templateId,
            int templateVersion,
            String locale,
            String channel,
            String subject,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long version) {}
}
