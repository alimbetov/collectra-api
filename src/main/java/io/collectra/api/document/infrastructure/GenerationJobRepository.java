package io.collectra.api.document.infrastructure;

import io.collectra.api.document.domain.GenerationJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {
    Optional<GenerationJob> findByIdAndTenantId(UUID id, UUID tenantId);
}
