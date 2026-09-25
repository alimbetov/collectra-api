package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.PromiseToPay;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, UUID> {
    Optional<PromiseToPay> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);

    Page<PromiseToPay> findAllByTenantIdAndCaseId(UUID tenantId, UUID caseId, Pageable pageable);
}
