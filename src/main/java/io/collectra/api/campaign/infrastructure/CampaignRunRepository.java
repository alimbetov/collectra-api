package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.CampaignRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRunRepository extends JpaRepository<CampaignRun, UUID> {
    Optional<CampaignRun> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CampaignRun> findAllByTenantIdAndCampaignIdOrderByCreatedAtDesc(
            UUID tenantId, UUID campaignId);
}
