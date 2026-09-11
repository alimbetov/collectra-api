package io.collectra.api.receivable.infrastructure;

import io.collectra.api.receivable.domain.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Invoice> findByTenantIdAndExternalId(UUID tenantId, String externalId);

    List<Invoice> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<Invoice> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    @Query(
            """
            select i
              from Invoice i
             where i.tenantId = :tenantId
               and i.outstandingAmount > 0
               and (:amountFrom is null or i.outstandingAmount >= :amountFrom)
               and (:amountTo is null or i.outstandingAmount <= :amountTo)
               and (:dueDateFrom is null or i.dueDate >= :dueDateFrom)
               and (:dueDateTo is null or i.dueDate <= :dueDateTo)
            """)
    Page<Invoice> findCampaignCandidates(
            @Param("tenantId") UUID tenantId,
            @Param("amountFrom") BigDecimal amountFrom,
            @Param("amountTo") BigDecimal amountTo,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            Pageable pageable);

    @Query(
            """
            select i
              from Invoice i
             where i.tenantId = :tenantId
               and i.customerId in :customerIds
               and i.outstandingAmount > 0
               and (:amountFrom is null or i.outstandingAmount >= :amountFrom)
               and (:amountTo is null or i.outstandingAmount <= :amountTo)
               and (:dueDateFrom is null or i.dueDate >= :dueDateFrom)
               and (:dueDateTo is null or i.dueDate <= :dueDateTo)
            """)
    Page<Invoice> findCampaignCandidatesForCustomers(
            @Param("tenantId") UUID tenantId,
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("amountFrom") BigDecimal amountFrom,
            @Param("amountTo") BigDecimal amountTo,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            Pageable pageable);
}
