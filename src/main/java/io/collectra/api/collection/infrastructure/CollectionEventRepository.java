package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.CollectionEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionEventRepository extends JpaRepository<CollectionEvent, UUID> {
    List<CollectionEvent> findAllByTenantIdAndCaseIdOrderByEventAtDescIdDesc(
            UUID tenantId, UUID caseId);
}
