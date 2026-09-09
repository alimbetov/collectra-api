package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.TemplateVersion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
    List<TemplateVersion> findAllByTemplateIdOrderByTemplateVersionDesc(UUID templateId);

    @Query(
            "select v from TemplateVersion v, DocumentTemplate t where v.id = :id "
                    + "and v.templateId = t.id and t.tenantId = :tenantId")
    Optional<TemplateVersion> findByIdAndTenantId(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
