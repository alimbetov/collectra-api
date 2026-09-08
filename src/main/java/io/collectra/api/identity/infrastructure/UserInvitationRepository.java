package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.UserInvitation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, UUID> {
    List<UserInvitation> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<UserInvitation> findByIdAndTenantId(UUID id, UUID tenantId);
    List<UserInvitation> findAllByTenantIdAndEmailIgnoreCaseAndStatus(UUID tenantId, String email, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from UserInvitation invitation where invitation.tokenHash = :hash")
    Optional<UserInvitation> findByTokenHashForUpdate(@Param("hash") String hash);
}
