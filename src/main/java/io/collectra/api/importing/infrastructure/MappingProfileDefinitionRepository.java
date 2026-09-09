package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.MappingProfileDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MappingProfileDefinitionRepository extends JpaRepository<MappingProfileDefinition, UUID> {
    Optional<MappingProfileDefinition> findByIdAndTenantId(UUID id, UUID tenantId);
    List<MappingProfileDefinition> findAllByTenantIdOrderByCodeAsc(UUID tenantId);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
