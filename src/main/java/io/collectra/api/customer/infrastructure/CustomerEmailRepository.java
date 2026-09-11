package io.collectra.api.customer.infrastructure;
import io.collectra.api.customer.domain.CustomerEmail;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerEmailRepository extends JpaRepository<CustomerEmail, UUID> { List<CustomerEmail> findAllByTenantIdAndCustomerId(UUID tenantId, UUID customerId); long countByTenantIdAndCustomerIdAndStatus(UUID tenantId, UUID customerId, String status); }
