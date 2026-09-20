package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerSegmentMember;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerSegmentMemberRepository
        extends JpaRepository<CustomerSegmentMember, UUID> {
    boolean existsByTenantIdAndCustomerIdAndSegmentId(
            UUID tenantId, UUID customerId, UUID segmentId);

    List<CustomerSegmentMember> findAllByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    List<CustomerSegmentMember> findAllByTenantIdAndCustomerIdIn(
            UUID tenantId, Collection<UUID> customerIds);

    void deleteByTenantIdAndCustomerIdAndSegmentId(UUID tenantId, UUID customerId, UUID segmentId);

    @Modifying
    @Query(
            value =
                    """
                    insert into customer_segment_members
                        (id, tenant_id, customer_id, segment_id, created_at, updated_at, version)
                    values
                        (:id, :tenantId, :customerId, :segmentId, current_timestamp, current_timestamp, 0)
                    on conflict (tenant_id, customer_id, segment_id) do nothing
                    """,
            nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("customerId") UUID customerId,
            @Param("segmentId") UUID segmentId);
}
