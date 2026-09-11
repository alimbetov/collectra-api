package io.collectra.api.receivable.infrastructure;
import io.collectra.api.receivable.domain.PaymentAllocation;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation,UUID>{List<PaymentAllocation> findAllByTenantIdAndPaymentId(UUID tenantId,UUID paymentId);@Query("select coalesce(sum(a.amount),0) from PaymentAllocation a where a.tenantId=:tenantId and a.paymentId=:paymentId") BigDecimal allocated(@Param("tenantId") UUID tenantId,@Param("paymentId") UUID paymentId);}
