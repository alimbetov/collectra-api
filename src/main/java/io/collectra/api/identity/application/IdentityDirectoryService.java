package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.UserAccountRepository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class IdentityDirectoryService {
    public static final int MAX_OPTION_PAGE_SIZE = 50;

    private final UserAccountRepository users;
    private final NamedParameterJdbcTemplate jdbc;

    public IdentityDirectoryService(UserAccountRepository users, NamedParameterJdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> users(UUID tenantId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return users.findAllByTenantIdAndIdIn(tenantId, userIds).stream()
                .map(UserSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserOptionPage userOptions(
            UUID tenantId, String search, String status, int page, int size) {
        String normalizedSearch = normalizeSearch(search);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("tenantId", tenantId);
        parameters.put("status", status);
        parameters.put("limit", size);
        parameters.put("offset", (long) page * size);
        StringBuilder where =
                new StringBuilder(
                        """
                        FROM tenant_memberships membership
                        JOIN user_accounts account ON account.id = membership.user_id
                        WHERE membership.tenant_id = :tenantId
                          AND membership.status = :status
                        """);
        if (normalizedSearch != null) {
            where.append(
                    """
  AND (
    lower(account.email) LIKE :searchPattern ESCAPE '\\'
    OR lower(coalesce(account.display_name, '')) LIKE :searchPattern ESCAPE '\\'
  )
""");
            parameters.put("searchPattern", escapeLike(normalizedSearch) + "%");
        }
        List<UserOption> items =
                jdbc.query(
                        """
                        SELECT account.id, account.email, account.display_name, membership.status
                        """
                                + where
                                + """
ORDER BY lower(coalesce(nullif(account.display_name, ''), account.email)),
         lower(account.email), account.id
LIMIT :limit OFFSET :offset
""",
                        parameters,
                        (result, row) -> {
                            String displayName = result.getString("display_name");
                            String email = result.getString("email");
                            return new UserOption(
                                    result.getObject("id", UUID.class),
                                    displayName == null || displayName.isBlank()
                                            ? email
                                            : displayName,
                                    email,
                                    displayName,
                                    result.getString("status"));
                        });
        long total = jdbc.queryForObject("SELECT count(*) " + where, parameters, Long.class);
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new UserOptionPage(items, page, size, total, totalPages, page + 1 < totalPages);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record UserSummary(UUID id, String email, String displayName, String status) {
        static UserSummary from(UserAccount value) {
            return new UserSummary(
                    value.getId(), value.getEmail(), value.getDisplayName(), value.getStatus());
        }

        public String label() {
            return displayName == null || displayName.isBlank() ? email : displayName;
        }
    }

    public record UserOption(
            UUID userId, String label, String email, String displayName, String status) {}

    public record UserOptionPage(
            List<UserOption> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
