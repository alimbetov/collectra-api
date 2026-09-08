package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.RefreshSession;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session where session.tokenHash = :hash")
    Optional<RefreshSession> findByTokenHashForUpdate(@Param("hash") String hash);

    Optional<RefreshSession> findByTokenHash(String tokenHash);
    List<RefreshSession> findAllByFamilyId(UUID familyId);

    @Modifying
    @Query("update RefreshSession session set session.revokedAt = current_timestamp where session.userId = :userId and session.revokedAt is null")
    int revokeAllByUserId(@Param("userId") UUID userId);
}
