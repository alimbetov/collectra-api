package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.FieldDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {
    @Query("select f from FieldDefinition f where f.tenantId is null or f.tenantId = :tenantId order by f.category, f.key")
    List<FieldDefinition> findAvailable(@Param("tenantId") UUID tenantId);
}
