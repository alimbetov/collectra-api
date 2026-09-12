package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.CollectionAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionActionRepository extends JpaRepository<CollectionAction, UUID> {
    Optional<CollectionAction> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);
    List<CollectionAction> findAllByTenantIdAndCaseIdOrderByDueAtAsc(UUID tenantId, UUID caseId);
}
