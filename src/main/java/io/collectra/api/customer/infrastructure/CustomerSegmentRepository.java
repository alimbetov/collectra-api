package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerSegment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerSegmentRepository extends JpaRepository<CustomerSegment, UUID> {
    Optional<CustomerSegment> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CustomerSegment> findByTenantIdAndCode(UUID tenantId, String code);

    List<CustomerSegment> findAllByTenantIdOrderByNameAsc(UUID tenantId);

    @Query(
            """
            select s
            from CustomerSegment s
            where s.tenantId = :tenantId
              and (:active is null or s.active = :active)
              and (
                    :searchEnabled = false
                    or lower(s.name) like concat(concat('%', :search), '%')
                    or lower(s.code) like concat(:search, '%')
              )
            """)
    Page<CustomerSegment> search(
            @Param("tenantId") UUID tenantId,
            @Param("searchEnabled") boolean searchEnabled,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable);
}
