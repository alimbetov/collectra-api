package io.collectra.api.campaign.api;

import io.collectra.api.campaign.application.CampaignAttachmentService;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/generated-pdf-attachment")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
public class CampaignAttachmentController {
    private final CampaignAttachmentService attachments;

    public CampaignAttachmentController(CampaignAttachmentService attachments) {
        this.attachments = attachments;
    }

    @PutMapping
    Response configure(@PathVariable UUID campaignId, @Valid @RequestBody Request request) {
        Campaign campaign =
                attachments.configureGeneratedPdf(
                        TenantContext.requireTenantId(),
                        campaignId,
                        request.enabled(),
                        request.required(),
                        request.revision());
        return Response.from(campaign);
    }

    record Request(boolean enabled, boolean required, @NotNull @Min(0) Long revision) {}

    record Response(UUID campaignId, boolean enabled, boolean required, long revision) {
        static Response from(Campaign campaign) {
            return new Response(
                    campaign.getId(),
                    campaign.isGeneratedPdfAttachment(),
                    campaign.isGeneratedPdfAttachmentRequired(),
                    campaign.getVersion());
        }
    }
}
