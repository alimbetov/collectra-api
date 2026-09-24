package io.collectra.api.platform.application;

import io.collectra.api.shared.error.InvalidRequestException;
import io.collectra.api.shared.error.TenantNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantQueryService {
    private static final Map<String, String> SORT_COLUMNS =
            Map.of(
                    "createdAt", "t.created_at",
                    "updatedAt", "t.updated_at",
                    "name", "t.name",
                    "slug", "t.slug",
                    "status", "t.status");

    private final NamedParameterJdbcTemplate jdbc;

    public PlatformTenantQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantItem> tenants(
            String search,
            String status,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            where.append(" and (lower(t.slug) like :search or lower(t.name) like :search) ");
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase();
            if (!"ACTIVE".equals(normalizedStatus) && !"BLOCKED".equals(normalizedStatus)) {
                throw new InvalidRequestException(
                        "INVALID_FILTER", "Unsupported tenant status: " + status);
            }
            where.append(" and t.status = :status ");
            params.addValue("status", normalizedStatus);
        }
        if (createdFrom != null) {
            where.append(" and t.created_at >= :createdFrom ");
            params.addValue("createdFrom", createdFrom);
        }
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new InvalidRequestException(
                    "INVALID_FILTER", "createdFrom must be before createdTo");
        }
        if (createdTo != null) {
            where.append(" and t.created_at < :createdTo ");
            params.addValue("createdTo", createdTo);
        }

        long total =
                jdbc.queryForObject("select count(*) from tenants t" + where, params, Long.class);

        String orderBy = orderBy(sort);
        params.addValue("limit", size);
        params.addValue("offset", page * size);

        List<TenantItem> items =
                jdbc.query(
                        """
                        select
                            t.id,
                            t.slug,
                            t.name,
                            t.status,
                            t.created_at,
                            t.updated_at,
                            t.version,
                            (
                                select count(*)
                                from tenant_memberships tm
                                join user_accounts ua on ua.id = tm.user_id
                                where tm.tenant_id = t.id
                                  and tm.status = 'ACTIVE'
                                  and ua.status = 'ACTIVE'
                            ) as active_users,
                            (
                                select count(*)
                                from campaigns c
                                where c.tenant_id = t.id
                                  and c.created_at >= now() - interval '30 days'
                            ) as campaigns_30d,
                            (
                                select count(*)
                                from messages m
                                where m.tenant_id = t.id
                                  and m.created_at >= now() - interval '30 days'
                            ) as messages_30d
                        from tenants t
                        """
                                + where
                                + " order by "
                                + orderBy
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) ->
                                new TenantItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("slug"),
                                        rs.getString("name"),
                                        rs.getString("status"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant(),
                                        rs.getLong("version"),
                                        rs.getLong("active_users"),
                                        rs.getLong("campaigns_30d"),
                                        rs.getLong("messages_30d")));

        return new PageResponse<>(
                items, page, size, total, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public TenantDetail tenant(UUID tenantId) {
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);
        List<TenantDetail> values =
                jdbc.query(
                        """
                        select
                            t.id,
                            t.slug,
                            t.name,
                            t.status,
                            t.created_at,
                            t.updated_at,
                            t.version,
                            (
                                select count(*)
                                from tenant_memberships tm
                                join user_accounts ua on ua.id = tm.user_id
                                where tm.tenant_id = t.id
                                  and tm.status = 'ACTIVE'
                                  and ua.status = 'ACTIVE'
                            ) as active_users,
                            (
                                select count(*)
                                from tenant_memberships tm
                                join user_accounts ua on ua.id = tm.user_id
                                where tm.tenant_id = t.id
                                  and (tm.status <> 'ACTIVE' or ua.status <> 'ACTIVE')
                            ) as blocked_users,
                            (select count(*) from customers c where c.tenant_id = t.id) as customers,
                            (select count(*) from campaigns c where c.tenant_id = t.id) as campaigns,
                            (select count(*) from messages m where m.tenant_id = t.id) as messages,
                            (
                                select count(*)
                                from generated_documents d
                                where d.tenant_id = t.id
                            ) as generated_documents,
                            (
                                select count(*)
                                from stored_file f
                                where f.tenant_id = t.id
                                  and f.status <> 'DELETED'
                            ) as files
                        from tenants t
                        where t.id = :tenantId
                        """,
                        params,
                        (rs, rowNum) ->
                                new TenantDetail(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("slug"),
                                        rs.getString("name"),
                                        rs.getString("status"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant(),
                                        rs.getLong("version"),
                                        rs.getLong("active_users"),
                                        rs.getLong("blocked_users"),
                                        rs.getLong("customers"),
                                        rs.getLong("campaigns"),
                                        rs.getLong("messages"),
                                        rs.getLong("generated_documents"),
                                        rs.getLong("files")));
        if (values.isEmpty()) {
            throw new TenantNotFoundException();
        }
        return values.get(0);
    }

    private String orderBy(String sort) {
        String value = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidRequestException("UNSUPPORTED_SORT", "Invalid tenant sort: " + value);
        }

        String field = parts[0];
        String column = SORT_COLUMNS.get(field);
        if (column == null) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported tenant sort: " + field);
        }

        String direction = parts.length == 1 ? "desc" : parts[1].toLowerCase();
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported tenant sort direction: " + parts[1]);
        }
        return column + " " + direction + ", t.id " + direction;
    }

    public record PageResponse<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {}

    public record TenantItem(
            UUID id,
            String slug,
            String name,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long revision,
            long activeUsers,
            long campaignsLast30d,
            long messagesLast30d) {}

    public record TenantDetail(
            UUID id,
            String slug,
            String name,
            String status,
            Instant createdAt,
            Instant updatedAt,
            long revision,
            long activeUsers,
            long blockedUsers,
            long customers,
            long campaigns,
            long messages,
            long generatedDocuments,
            long files) {}
}
