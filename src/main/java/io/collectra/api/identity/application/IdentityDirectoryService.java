package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityDirectoryService {
    private final UserAccountRepository users;

    public IdentityDirectoryService(UserAccountRepository users) {
        this.users = users;
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

    public record UserSummary(UUID id, String email, String displayName, String status) {
        static UserSummary from(UserAccount value) {
            return new UserSummary(
                    value.getId(), value.getEmail(), value.getDisplayName(), value.getStatus());
        }

        public String label() {
            return displayName == null || displayName.isBlank() ? email : displayName;
        }
    }
}
