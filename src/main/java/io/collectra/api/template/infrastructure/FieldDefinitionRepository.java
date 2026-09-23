package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.FieldDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldDefinitionRepository extends JpaRepository<FieldDefinition, UUID> {
    @Query(
            "select f from FieldDefinition f where (f.tenantId is null or f.tenantId = :tenantId) "
                    + "and f.status = 'ACTIVE' order by f.category, f.key")
    List<FieldDefinition> findAvailable(@Param("tenantId") UUID tenantId);

    @Query(
            value =
                    "select f from FieldDefinition f where (f.tenantId is null or f.tenantId = :tenantId) "
                            + "and f.status = 'ACTIVE' order by f.category, f.key",
            countQuery =
                    "select count(f) from FieldDefinition f where (f.tenantId is null or f.tenantId = :tenantId) "
                            + "and f.status = 'ACTIVE'")
    Page<FieldDefinition> findAvailable(@Param("tenantId") UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndKeyIgnoreCase(UUID tenantId, String key);

    boolean existsByTenantIdIsNullAndKeyIgnoreCase(String key);
}
