package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.UserAccount;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
    Optional<UserAccount> findByTenantIdIsNullAndEmailIgnoreCase(String email);
    Optional<UserAccount> findByIdAndTenantIdIsNull(UUID id);
    List<UserAccount> findAllByTenantIdIsNullOrderByEmailAsc();
    List<UserAccount> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}
