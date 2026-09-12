package io.collectra.api.document.infrastructure;

import io.collectra.api.document.domain.GenerationJob;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
    Optional<GenerationJob> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GenerationJob> findLockedByIdAndTenantId(UUID id, UUID tenantId);
}
