package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}
