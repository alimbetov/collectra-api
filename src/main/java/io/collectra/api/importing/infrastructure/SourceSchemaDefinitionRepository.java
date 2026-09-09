package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.SourceSchemaDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceSchemaDefinitionRepository extends JpaRepository<SourceSchemaDefinition, UUID> {
    Optional<SourceSchemaDefinition> findByIdAndTenantId(UUID id, UUID tenantId);
    List<SourceSchemaDefinition> findAllByTenantIdOrderByCodeAsc(UUID tenantId);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
