package io.collectra.api.campaign.infrastructure;

import io.collectra.api.campaign.domain.CampaignRecipient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRecipientRepository extends JpaRepository<CampaignRecipient, UUID> {
    Optional<CampaignRecipient> findByIdAndTenantId(UUID id, UUID tenantId);

    List<CampaignRecipient> findAllByTenantIdAndRunIdOrderByCreatedAtAsc(
            UUID tenantId, UUID runId);
}
