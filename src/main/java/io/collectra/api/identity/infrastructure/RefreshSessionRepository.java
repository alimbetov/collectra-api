package io.collectra.api.identity.infrastructure;

import io.collectra.api.identity.domain.RefreshSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String tokenHash);

    List<RefreshSession> findAllByFamilyId(UUID familyId);
}
