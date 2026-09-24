package io.collectra.api.platform.application;

import io.collectra.api.shared.error.InvalidRequestException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserQueryService {
    private static final Map<String, String> SORT_COLUMNS =
            Map.of(
                    "createdAt", "tm.created_at",
                    "updatedAt", "tm.updated_at",
                    "email", "ua.email",
                    "displayName", "ua.display_name",
                    "tenantSlug", "t.slug",
                    "membershipStatus", "tm.status",
                    "accountStatus", "ua.status");

    private final NamedParameterJdbcTemplate jdbc;

    public PlatformUserQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserItem> users(
            String search,
            UUID tenantId,
            String tenantSlug,
            String membershipStatus,
            String accountStatus,
            String roleCode,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        StringBuilder where = new StringBuilder(" where 1=1 ");
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            where.append(
                    " and (lower(ua.email) like :search or lower(coalesce(ua.display_name, '')) like :search) ");
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (tenantId != null) {
            where.append(" and tm.tenant_id = :tenantId ");
            params.addValue("tenantId", tenantId);
        }
        if (tenantSlug != null && !tenantSlug.isBlank()) {
            where.append(" and lower(t.slug) = :tenantSlug ");
            params.addValue("tenantSlug", tenantSlug.trim().toLowerCase());
        }
        appendStatusFilter(where, params, "tm.status", "membershipStatus", membershipStatus);
        appendStatusFilter(where, params, "ua.status", "accountStatus", accountStatus);
        if (roleCode != null && !roleCode.isBlank()) {
            where.append(
                    """
                     and exists (
                         select 1
                           from membership_roles filter_mr
                           join roles filter_r on filter_r.id = filter_mr.role_id
                          where filter_mr.membership_id = tm.id
                            and lower(filter_r.code) = :roleCode
                     )
                    """);
            params.addValue("roleCode", roleCode.trim().toLowerCase());
        }
        if (createdFrom != null) {
            where.append(" and tm.created_at >= :createdFrom ");
            params.addValue("createdFrom", createdFrom);
        }
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new InvalidRequestException(
                    "INVALID_FILTER", "createdFrom must be before createdTo");
        }
        if (createdTo != null) {
            where.append(" and tm.created_at < :createdTo ");
            params.addValue("createdTo", createdTo);
        }

        long total =
                jdbc.queryForObject(
                        """
                        select count(*)
                          from tenant_memberships tm
                          join user_accounts ua on ua.id = tm.user_id
                          join tenants t on t.id = tm.tenant_id
                        """
                                + where,
                        params,
                        Long.class);

        params.addValue("limit", size);
        params.addValue("offset", page * size);

        List<UserItem> items =
                jdbc.query(
                        """
                        select
                            ua.id as user_id,
                            tm.id as membership_id,
                            t.id as tenant_id,
                            t.slug as tenant_slug,
                            t.name as tenant_name,
                            ua.email,
                            ua.display_name,
                            ua.status as account_status,
                            tm.status as membership_status,
                            case
                                when t.status = 'ACTIVE'
                                 and ua.status = 'ACTIVE'
                                 and tm.status = 'ACTIVE' then 'ACTIVE'
                                else 'BLOCKED'
                            end as effective_access_status,
                            coalesce(
                                string_agg(distinct r.code, ',' order by r.code),
                                ''
                            ) as role_codes,
                            tm.created_at,
                            tm.updated_at,
                            tm.version
                          from tenant_memberships tm
                          join user_accounts ua on ua.id = tm.user_id
                          join tenants t on t.id = tm.tenant_id
                          left join membership_roles mr on mr.membership_id = tm.id
                          left join roles r on r.id = mr.role_id
                        """
                                + where
                                + """
                         group by
                            ua.id, tm.id, t.id, t.slug, t.name,
                            ua.email, ua.display_name, ua.status, tm.status,
                            tm.created_at, tm.updated_at, tm.version, t.status
                         order by
                        """
                                + orderBy(sort)
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) ->
                                new UserItem(
                                        rs.getObject("user_id", UUID.class),
                                        rs.getObject("membership_id", UUID.class),
                                        rs.getObject("tenant_id", UUID.class),
                                        rs.getString("tenant_slug"),
                                        rs.getString("tenant_name"),
                                        rs.getString("email"),
                                        rs.getString("display_name"),
                                        rs.getString("account_status"),
                                        rs.getString("membership_status"),
                                        rs.getString("effective_access_status"),
                                        splitCodes(rs.getString("role_codes")),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant(),
                                        rs.getLong("version")));

        return new PageResponse<>(
                items, page, size, total, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    public UserDetail user(UUID userId) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId);
        List<UserDetail> rows =
                jdbc.query(
                        """
                        select
                            ua.id as user_id,
                            tm.id as membership_id,
                            t.id as tenant_id,
                            t.slug as tenant_slug,
                            t.name as tenant_name,
                            ua.email,
                            ua.display_name,
                            ua.locale,
                            ua.timezone,
                            ua.status as account_status,
                            tm.status as membership_status,
                            case
                                when t.status = 'ACTIVE'
                                 and ua.status = 'ACTIVE'
                                 and tm.status = 'ACTIVE' then 'ACTIVE'
                                else 'BLOCKED'
                            end as effective_access_status,
                            ua.authorization_version,
                            coalesce(
                                string_agg(distinct r.code, ',' order by r.code),
                                ''
                            ) as role_codes,
                            tm.created_at,
                            tm.updated_at,
                            tm.version,
                            count(distinct rs.id) filter (
                                where rs.context_type = 'TENANT'
                            ) as session_count,
                            count(distinct rs.id) filter (
                                where rs.context_type = 'TENANT'
                                  and rs.revoked_at is null
                                  and rs.expires_at > now()
                            ) as active_session_count
                          from tenant_memberships tm
                          join user_accounts ua on ua.id = tm.user_id
                          join tenants t on t.id = tm.tenant_id
                          left join membership_roles mr on mr.membership_id = tm.id
                          left join roles r on r.id = mr.role_id
                          left join refresh_sessions rs
                            on rs.membership_id = tm.id
                           and rs.user_id = ua.id
                         where ua.id = :userId
                         group by
                            ua.id, tm.id, t.id, t.slug, t.name,
                            ua.email, ua.display_name, ua.locale, ua.timezone,
                            ua.status, tm.status, ua.authorization_version,
                            tm.created_at, tm.updated_at, tm.version, t.status
                        """,
                        params,
                        (rs, rowNum) ->
                                new UserDetail(
                                        rs.getObject("user_id", UUID.class),
                                        rs.getObject("membership_id", UUID.class),
                                        rs.getObject("tenant_id", UUID.class),
                                        rs.getString("tenant_slug"),
                                        rs.getString("tenant_name"),
                                        rs.getString("email"),
                                        rs.getString("display_name"),
                                        rs.getString("locale"),
                                        rs.getString("timezone"),
                                        rs.getString("account_status"),
                                        rs.getString("membership_status"),
                                        rs.getString("effective_access_status"),
                                        rs.getLong("authorization_version"),
                                        splitCodes(rs.getString("role_codes")),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant(),
                                        rs.getLong("version"),
                                        rs.getLong("session_count"),
                                        rs.getLong("active_session_count")));
        if (rows.isEmpty()) {
            throw new java.util.NoSuchElementException("Platform user not found");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionItem> sessions(UUID membershipId, int page, int size) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("membershipId", membershipId)
                        .addValue("limit", size)
                        .addValue("offset", page * size);

        UUID membership =
                jdbc.query(
                                """
                                select id
                                  from tenant_memberships
                                 where id = :membershipId
                                """,
                                params,
                                (rs, rowNum) -> rs.getObject("id", UUID.class))
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () -> new java.util.NoSuchElementException("Membership not found"));

        long total =
                jdbc.queryForObject(
                        """
                        select count(*)
                          from refresh_sessions
                         where membership_id = :membershipId
                           and context_type = 'TENANT'
                        """,
                        params,
                        Long.class);

        List<SessionItem> items =
                jdbc.query(
                        """
                        select id, family_id, expires_at, revoked_at, last_used_at,
                               user_agent, source_ip, created_at
                          from refresh_sessions
                         where membership_id = :membershipId
                           and context_type = 'TENANT'
                         order by created_at desc, id desc
                         limit :limit offset :offset
                        """,
                        params,
                        (rs, rowNum) ->
                                new SessionItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("family_id", UUID.class),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("expires_at").toInstant(),
                                        timestamp(rs, "revoked_at"),
                                        timestamp(rs, "last_used_at"),
                                        rs.getString("user_agent"),
                                        rs.getString("source_ip")));

        return new PageResponse<>(
                items, page, size, total, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    private static Instant timestamp(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private void appendStatusFilter(
            StringBuilder where,
            MapSqlParameterSource params,
            String column,
            String parameter,
            String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"BLOCKED".equals(normalized)) {
            throw new InvalidRequestException(
                    "INVALID_FILTER", "Unsupported status filter: " + value);
        }
        where.append(" and ").append(column).append(" = :").append(parameter).append(" ");
        params.addValue(parameter, normalized);
    }

    private String orderBy(String sort) {
        String value = sort == null || sort.isBlank() ? "createdAt,desc" : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidRequestException("UNSUPPORTED_SORT", "Invalid user sort: " + value);
        }

        String column = SORT_COLUMNS.get(parts[0]);
        if (column == null) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported user sort: " + parts[0]);
        }
        String direction = parts.length == 1 ? "desc" : parts[1].toLowerCase();
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported user sort direction: " + parts[1]);
        }
        return column + " " + direction + ", tm.id " + direction;
    }

    private static List<String> splitCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).filter(code -> !code.isBlank()).toList();
    }

    public record PageResponse<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {}

    public record UserItem(
            UUID userId,
            UUID membershipId,
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            String email,
            String displayName,
            String accountStatus,
            String membershipStatus,
            String effectiveAccessStatus,
            List<String> roleCodes,
            Instant createdAt,
            Instant updatedAt,
            long revision) {}

    public record UserDetail(
            UUID userId,
            UUID membershipId,
            UUID tenantId,
            String tenantSlug,
            String tenantName,
            String email,
            String displayName,
            String locale,
            String timezone,
            String accountStatus,
            String membershipStatus,
            String effectiveAccessStatus,
            long authorizationVersion,
            List<String> roleCodes,
            Instant createdAt,
            Instant updatedAt,
            long revision,
            long sessionCount,
            long activeSessionCount) {}

    public record SessionItem(
            UUID id,
            UUID familyId,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt,
            Instant lastUsedAt,
            String userAgent,
            String sourceIp) {}
}
