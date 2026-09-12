package io.collectra.api.collection.infrastructure;

import io.collectra.api.collection.domain.PromiseToPay;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromiseToPayRepository extends JpaRepository<PromiseToPay, UUID> {
    Optional<PromiseToPay> findByIdAndTenantIdAndCaseId(UUID id, UUID tenantId, UUID caseId);
    List<PromiseToPay> findAllByTenantIdAndCaseIdOrderByCreatedAtDesc(UUID tenantId, UUID caseId);
}
