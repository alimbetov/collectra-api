package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.CampaignRunTemplateBinding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRunTemplateBindingRepository
        extends JpaRepository<CampaignRunTemplateBinding, UUID> {
    List<CampaignRunTemplateBinding> findAllByTenantIdAndCampaignRunId(
            UUID tenantId, UUID campaignRunId);

    Optional<CampaignRunTemplateBinding> findByTenantIdAndCampaignRunIdAndRequestedLocale(
            UUID tenantId, UUID campaignRunId, String requestedLocale);
}
