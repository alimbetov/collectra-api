package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.MappingProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MappingProfileRepository extends JpaRepository<MappingProfile, UUID> {
    Optional<MappingProfile> findByIdAndTenantId(UUID id, UUID tenantId);
    List<MappingProfile> findAllByTenantIdOrderByCodeAscProfileVersionDesc(UUID tenantId);
    List<MappingProfile> findAllByDefinitionIdOrderByProfileVersionDesc(UUID definitionId);
    Optional<MappingProfile> findTopByDefinitionIdOrderByProfileVersionDesc(UUID definitionId);
}
