package io.collectra.api.document.infrastructure;

import io.collectra.api.document.domain.GenerationJob;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
    Optional<GenerationJob> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GenerationJob> findLockedById(UUID id);
}
