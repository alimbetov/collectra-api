package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.Customer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository
        extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndExternalId(UUID tenantId, String externalId);

    List<Customer> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Customer> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
