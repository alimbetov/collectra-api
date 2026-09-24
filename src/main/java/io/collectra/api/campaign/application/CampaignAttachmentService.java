package io.collectra.api.campaign.application;

import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.infrastructure.CampaignRepository;
import io.collectra.api.shared.error.BusinessConflictException;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignAttachmentService {
    private final CampaignRepository campaigns;

    public CampaignAttachmentService(CampaignRepository campaigns) {
        this.campaigns = campaigns;
    }

    @Transactional
    public Campaign configureGeneratedPdf(
            UUID tenantId, UUID campaignId, boolean enabled, boolean required) {
        Campaign campaign =
                campaigns
                        .findByIdAndTenantId(campaignId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Campaign not found"));
        campaign.configureGeneratedPdfAttachment(enabled, required);
        return campaign;
    }

    @Transactional
    public Campaign configureGeneratedPdf(
            UUID tenantId,
            UUID campaignId,
            boolean enabled,
            boolean required,
            long expectedRevision) {
        Campaign campaign =
                campaigns
                        .findLockedByIdAndTenantId(campaignId, tenantId)
                        .orElseThrow(() -> new NoSuchElementException("Campaign not found"));
        if (campaign.getVersion() != expectedRevision) {
            throw new BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Campaign revision conflict: expected "
                            + expectedRevision
                            + " but was "
                            + campaign.getVersion());
        }
        campaign.configureGeneratedPdfAttachment(enabled, required);
        return campaign;
    }
}
