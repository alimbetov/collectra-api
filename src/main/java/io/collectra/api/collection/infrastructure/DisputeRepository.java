package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.Dispute;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    Optional<Dispute> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);
    List<Dispute> findAllByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
}
