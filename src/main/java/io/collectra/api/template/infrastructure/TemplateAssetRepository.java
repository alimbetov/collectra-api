package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.TemplateAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateAssetRepository extends JpaRepository<TemplateAsset, UUID> {
    List<TemplateAsset> findAllByTenantIdAndStatusOrderByAssetKeyAsc(UUID tenantId, String status);

    Optional<TemplateAsset> findByTenantIdAndAssetKeyAndStatus(
            UUID tenantId, String assetKey, String status);

    Optional<TemplateAsset> findByIdAndTenantId(UUID id, UUID tenantId);
}
