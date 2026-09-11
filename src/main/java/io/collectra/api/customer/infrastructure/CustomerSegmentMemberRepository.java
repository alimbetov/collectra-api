package io.collectra.api.customer.infrastructure;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CustomerSegmentMemberRepository extends JpaRepository<CustomerSegmentMember, UUID> { boolean existsByTenantIdAndCustomerIdAndSegmentId(UUID tenantId, UUID customerId, UUID segmentId); List<CustomerSegmentMember> findAllByTenantIdAndCustomerId(UUID tenantId, UUID customerId); void deleteByTenantIdAndCustomerIdAndSegmentId(UUID tenantId, UUID customerId, UUID segmentId); }
