package io.collectra.api.contract.infrastructure;

import io.collectra.api.contract.domain.Contract;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContractRepository
        extends JpaRepository<Contract, UUID>, JpaSpecificationExecutor<Contract> {
    Optional<Contract> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Contract> findByTenantIdAndExternalId(UUID tenantId, String externalId);
}
