package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.TemplateChannel;
import io.collectra.api.template.domain.TemplateVersion;
import io.collectra.api.template.domain.TemplateVersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
    List<TemplateVersion> findAllByTemplateIdOrderByTemplateVersionDesc(UUID templateId);

    List<TemplateVersion> findAllByTemplateIdAndLocaleOrderByTemplateVersionDesc(
            UUID templateId, String locale);

    List<TemplateVersion> findAllByTemplateIdAndLocaleAndChannelOrderByTemplateVersionDesc(
            UUID templateId, String locale, TemplateChannel channel);

    boolean existsByTemplateIdAndLocaleAndChannelAndStatus(
            UUID templateId,
            String locale,
            TemplateChannel channel,
            TemplateVersionStatus status);

    Optional<TemplateVersion> findFirstByTemplateIdAndLocaleAndChannelAndStatusOrderByTemplateVersionDesc(
            UUID templateId,
            String locale,
            TemplateChannel channel,
            TemplateVersionStatus status);

    @Query(
            "select v from TemplateVersion v, DocumentTemplate t where v.id = :id "
                    + "and v.templateId = t.id and t.tenantId = :tenantId")
    Optional<TemplateVersion> findByIdAndTenantId(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query(
            "select v from TemplateVersion v, DocumentTemplate t where v.id in :ids "
                    + "and v.templateId = t.id and t.tenantId = :tenantId")
    List<TemplateVersion> findAllByIdsAndTenantId(
            @Param("ids") Collection<UUID> ids, @Param("tenantId") UUID tenantId);
}
