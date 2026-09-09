package io.collectra.api.importing.infrastructure;

import io.collectra.api.importing.domain.MappingRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MappingRuleRepository extends JpaRepository<MappingRule, UUID> {
    List<MappingRule> findAllByMappingProfileId(UUID mappingProfileId);
    java.util.Optional<MappingRule> findByIdAndMappingProfileId(UUID id, UUID mappingProfileId);
}
