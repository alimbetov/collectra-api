package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.customer.domain.CustomerType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Customer> findByTenantIdAndExternalId(UUID tenantId, String externalId);

    List<Customer> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Customer> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    @Query(
            """
            select c
            from Customer c
            where c.tenantId = :tenantId
              and (:status is null or c.status = :status)
              and (:customerType is null or c.customerType = :customerType)
              and (:managerId is null or c.managerUserId = :managerId)
              and (:externalId is null or c.externalId = :externalId)
              and (:createdFrom is null or c.createdAt >= :createdFrom)
              and (:createdTo is null or c.createdAt <= :createdTo)
              and (
                    :search is null
                    or lower(c.displayName) like concat(concat('%', :search), '%')
                    or lower(c.externalId) like concat(:search, '%')
              )
              and (
                    :segmentId is null
                    or exists (
                        select m.id
                        from CustomerSegmentMember m
                        where m.tenantId = :tenantId
                          and m.customerId = c.id
                          and m.segmentId = :segmentId
                    )
              )
              and (
                    :email is null
                    or exists (
                        select e.id
                        from CustomerEmail e
                        where e.tenantId = :tenantId
                          and e.customerId = c.id
                          and lower(e.email) = :email
                    )
              )
              and (
                    :phone is null
                    or exists (
                        select p.id
                        from CustomerPhone p
                        where p.tenantId = :tenantId
                          and p.customerId = c.id
                          and p.normalizedPhone = :phone
                    )
              )
            """)
    Page<Customer> search(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("status") CustomerStatus status,
            @Param("customerType") CustomerType customerType,
            @Param("managerId") UUID managerId,
            @Param("segmentId") UUID segmentId,
            @Param("externalId") String externalId,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable);
}
