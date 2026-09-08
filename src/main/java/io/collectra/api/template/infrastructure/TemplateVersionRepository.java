package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.TemplateVersion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {
    List<TemplateVersion> findAllByTemplateIdOrderByTemplateVersionDesc(UUID templateId);
}
