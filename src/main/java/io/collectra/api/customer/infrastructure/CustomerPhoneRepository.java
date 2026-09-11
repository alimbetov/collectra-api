package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerPhone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPhoneRepository extends JpaRepository<CustomerPhone, UUID> {
    List<CustomerPhone> findAllByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    long countByTenantIdAndCustomerIdAndStatus(UUID tenantId, UUID customerId, String status);
}
