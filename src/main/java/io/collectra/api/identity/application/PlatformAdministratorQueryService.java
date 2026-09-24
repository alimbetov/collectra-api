package io.collectra.api.identity.application;

import io.collectra.api.shared.error.InvalidRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdministratorQueryService {
    private static final Map<String, String> SORT_COLUMNS =
            Map.of(
                    "createdAt", "ua.created_at",
                    "updatedAt", "ua.updated_at",
                    "email", "ua.email",
                    "status", "ua.status");

    private final NamedParameterJdbcTemplate jdbc;

    public PlatformAdministratorQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdministratorItem> administrators(
            String search, String status, int page, int size, String sort) {
        StringBuilder where =
                new StringBuilder(
                        """
                         where ua.tenant_id is null
                           and exists (
                               select 1
                                 from platform_user_roles pur
                                 join roles r on r.id = pur.role_id
                                where pur.user_id = ua.id
                                  and r.code = 'PLATFORM_SUPER_ADMIN'
                           )
                        """);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (search != null && !search.isBlank()) {
            where.append(" and lower(ua.email) like :search ");
            params.addValue("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            if (!"ACTIVE".equals(normalized) && !"BLOCKED".equals(normalized)) {
                throw new InvalidRequestException(
                        "INVALID_FILTER", "Unsupported administrator status: " + status);
            }
            where.append(" and ua.status = :status ");
            params.addValue("status", normalized);
        }

        long total =
                jdbc.queryForObject(
                        "select count(*) from user_accounts ua" + where, params, Long.class);

        params.addValue("limit", size);
        params.addValue("offset", page * size);
        List<AdministratorItem> items =
                jdbc.query(
                        """
                        select ua.id, ua.email, ua.status, ua.authorization_version,
                               ua.created_at, ua.updated_at, ua.version
                          from user_accounts ua
                        """
                                + where
                                + " order by "
                                + orderBy(sort)
                                + " limit :limit offset :offset",
                        params,
                        (rs, rowNum) ->
                                new AdministratorItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("email"),
                                        rs.getString("status"),
                                        rs.getLong("authorization_version"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant(),
                                        rs.getLong("version")));

        return new PageResponse<>(
                items, page, size, total, total == 0 ? 0 : (int) Math.ceil((double) total / size));
    }

    private String orderBy(String sort) {
        String value = sort == null || sort.isBlank() ? "email,asc" : sort.trim();
        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Invalid administrator sort: " + value);
        }
        String column = SORT_COLUMNS.get(parts[0]);
        if (column == null) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT", "Unsupported administrator sort: " + parts[0]);
        }
        String direction = parts.length == 1 ? "asc" : parts[1].toLowerCase();
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_SORT",
                    "Unsupported administrator sort direction: " + parts[1]);
        }
        return column + " " + direction + ", ua.id " + direction;
    }

    public record PageResponse<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {}

    public record AdministratorItem(
            UUID id,
            String email,
            String status,
            long authorizationVersion,
            Instant createdAt,
            Instant updatedAt,
            long revision) {}
}
