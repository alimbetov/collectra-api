package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerEmail;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerEmailRepository extends JpaRepository<CustomerEmail, UUID> {
    Optional<CustomerEmail> findByIdAndTenantIdAndCustomerId(
            UUID id, UUID tenantId, UUID customerId);

    List<CustomerEmail> findAllByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    List<CustomerEmail> findAllByTenantIdAndCustomerIdIn(
            UUID tenantId, Collection<UUID> customerIds);

    List<CustomerEmail> findAllByTenantIdAndCustomerIdAndPrimaryTrueAndStatus(
            UUID tenantId, UUID customerId, String status);

    boolean existsByTenantIdAndCustomerIdAndEmail(UUID tenantId, UUID customerId, String email);

    long countByTenantIdAndCustomerIdAndStatus(UUID tenantId, UUID customerId, String status);
}
