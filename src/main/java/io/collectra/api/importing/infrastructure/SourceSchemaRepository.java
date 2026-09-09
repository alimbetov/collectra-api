package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.SourceSchema;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceSchemaRepository extends JpaRepository<SourceSchema, UUID> {
    Optional<SourceSchema> findByIdAndTenantId(UUID id, UUID tenantId);
    List<SourceSchema> findAllByTenantIdOrderByCodeAscSchemaVersionDesc(UUID tenantId);
    List<SourceSchema> findAllByDefinitionIdOrderBySchemaVersionDesc(UUID definitionId);
    Optional<SourceSchema> findTopByDefinitionIdOrderBySchemaVersionDesc(UUID definitionId);
}
