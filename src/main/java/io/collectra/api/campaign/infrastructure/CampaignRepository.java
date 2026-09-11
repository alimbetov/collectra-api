package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.Campaign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    Optional<Campaign> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Campaign> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
