package io.collectra.api.customer.infrastructure;
import io.collectra.api.customer.domain.CustomerSegment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, UUID> { Optional<CustomerSegment> findByIdAndTenantId(UUID id, UUID tenantId); List<CustomerSegment> findAllByTenantIdOrderByNameAsc(UUID tenantId); }
