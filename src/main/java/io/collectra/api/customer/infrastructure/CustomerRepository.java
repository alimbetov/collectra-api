package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.Customer;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<Customer> findByTenantIdAndExternalId(UUID tenantId, String externalId);
    List<Customer> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
