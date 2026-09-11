package io.collectra.api.receivable.infrastructure;
import io.collectra.api.receivable.domain.Payment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment,UUID>{Optional<Payment> findByIdAndTenantId(UUID id,UUID tenantId);Optional<Payment> findByTenantIdAndExternalId(UUID tenantId,String externalId);List<Payment> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);}
