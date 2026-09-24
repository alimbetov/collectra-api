package io.collectra.api.template.infrastructure;

import io.collectra.api.template.domain.TemplateAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateAssetRepository extends JpaRepository<TemplateAsset, UUID> {
    List<TemplateAsset> findAllByTenantIdAndStatusOrderByAssetKeyAsc(UUID tenantId, String status);

    @Query(
            value =
                    "select a from TemplateAsset a where a.tenantId = :tenantId and a.status = :status "
                            + "and (:search is null or lower(a.assetKey) like :search "
                            + "or lower(coalesce(a.altText, '')) like :search)",
            countQuery =
                    "select count(a) from TemplateAsset a where a.tenantId = :tenantId and a.status = :status "
                            + "and (:search is null or lower(a.assetKey) like :search "
                            + "or lower(coalesce(a.altText, '')) like :search)")
    Page<TemplateAsset> findAvailable(
            @Param("tenantId") UUID tenantId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    Optional<TemplateAsset> findByTenantIdAndAssetKeyAndStatus(
            UUID tenantId, String assetKey, String status);

    Optional<TemplateAsset> findByIdAndTenantId(UUID id, UUID tenantId);
}
