package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.DocumentTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {
    Optional<DocumentTemplate> findByIdAndTenantId(UUID id, UUID tenantId);
    List<DocumentTemplate> findAllByTenantIdOrderByNameAsc(UUID tenantId);
}
