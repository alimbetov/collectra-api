package io.collectra.api.receivable.infrastructure;

import io.collectra.api.receivable.domain.AllocationStatus;
import io.collectra.api.receivable.domain.PaymentAllocation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    List<PaymentAllocation> findAllByTenantIdAndPaymentIdOrderByCreatedAtAsc(
            UUID tenantId, UUID paymentId);

    List<PaymentAllocation> findAllByTenantIdAndInvoiceIdOrderByCreatedAtAsc(
            UUID tenantId, UUID invoiceId);

    Optional<PaymentAllocation> findByIdAndTenantIdAndPaymentId(
            UUID id, UUID tenantId, UUID paymentId);

    Optional<PaymentAllocation> findByTenantIdAndCommandId(UUID tenantId, UUID commandId);

    @Query(
            "select coalesce(sum(a.amount),0) from PaymentAllocation a "
                    + "where a.tenantId=:tenantId and a.paymentId=:paymentId and a.status=:status")
    BigDecimal allocated(
            @Param("tenantId") UUID tenantId,
            @Param("paymentId") UUID paymentId,
            @Param("status") AllocationStatus status);
}
