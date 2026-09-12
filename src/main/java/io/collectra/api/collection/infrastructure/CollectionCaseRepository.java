package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.CollectionCase;
import io.collectra.api.collection.domain.CollectionCaseStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CollectionCaseRepository
        extends JpaRepository<CollectionCase, UUID>, JpaSpecificationExecutor<CollectionCase> {
    Optional<CollectionCase> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndInvoiceIdAndStatusIn(
            UUID tenantId, UUID invoiceId, Iterable<CollectionCaseStatus> statuses);
}
