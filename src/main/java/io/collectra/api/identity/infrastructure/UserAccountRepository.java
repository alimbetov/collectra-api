package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForCredentialMigration(@Param("id") UUID id);

    Optional<UserAccount> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    Optional<UserAccount> findByTenantIdIsNullAndEmailIgnoreCase(String email);

    Optional<UserAccount> findByIdAndTenantIdIsNull(UUID id);

    List<UserAccount> findAllByTenantIdIsNullOrderByEmailAsc();

    List<UserAccount> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}
