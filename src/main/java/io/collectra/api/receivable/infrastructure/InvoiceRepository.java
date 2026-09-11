package io.collectra.api.receivable.infrastructure;
import io.collectra.api.receivable.domain.Invoice;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InvoiceRepository extends JpaRepository<Invoice,UUID>{Optional<Invoice> findByIdAndTenantId(UUID id,UUID tenantId);Optional<Invoice> findByTenantIdAndExternalId(UUID tenantId,String externalId);List<Invoice> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);}
