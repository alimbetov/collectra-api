package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.Dispute;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    Optional<Dispute> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);

    Page<Dispute> findAllByTenantIdAndCaseId(UUID tenantId, UUID caseId, Pageable pageable);
}
