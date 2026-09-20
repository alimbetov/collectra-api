package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerSegment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerSegmentRepository
        extends JpaRepository<CustomerSegment, UUID>, JpaSpecificationExecutor<CustomerSegment> {
    Optional<CustomerSegment> findByIdAndTenantId(UUID id, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select segment from CustomerSegment segment where segment.id = :id and"
                    + " segment.tenantId = :tenantId")
    Optional<CustomerSegment> findForUpdateByIdAndTenantId(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);

    Optional<CustomerSegment> findByTenantIdAndCode(UUID tenantId, String code);

    List<CustomerSegment> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    List<CustomerSegment> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
}
