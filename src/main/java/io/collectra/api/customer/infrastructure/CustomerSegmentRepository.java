package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerSegment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerSegmentRepository
        extends JpaRepository<CustomerSegment, UUID>, JpaSpecificationExecutor<CustomerSegment> {
    Optional<CustomerSegment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CustomerSegment> findByTenantIdAndCode(UUID tenantId, String code);

    List<CustomerSegment> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    List<CustomerSegment> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
