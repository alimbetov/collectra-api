package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.CollectionEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionEventRepository extends JpaRepository<CollectionEvent, UUID> {
    Page<CollectionEvent> findAllByTenantIdAndCaseId(UUID tenantId, UUID caseId, Pageable pageable);
}
