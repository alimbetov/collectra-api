package io.collectra.api.campaign.api;

import io.collectra.api.campaign.application.CampaignEligibilityService;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignRecipient;
import io.collectra.api.campaign.domain.CampaignRecipientStatus;
import io.collectra.api.campaign.domain.CampaignRun;
import io.collectra.api.campaign.domain.CampaignRunStatus;
import io.collectra.api.campaign.domain.CampaignStatus;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CampaignController {
    private final CampaignService campaigns;
    private final CampaignEligibilityService eligibility;

    public CampaignController(CampaignService campaigns, CampaignEligibilityService eligibility) {
        this.campaigns = campaigns;
        this.eligibility = eligibility;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CampaignResponse create(@Valid @RequestBody CreateCampaignRequest request) {
        return CampaignResponse.from(
                campaigns.create(
                        tenant(),
                        request.name(),
                        request.templateVersionId(),
                        request.channel(),
                        request.scheduledAt(),
                        request.selection(),
                        null));
    }

    @GetMapping
    List<CampaignResponse> list() {
        return campaigns.campaigns(tenant()).stream().map(CampaignResponse::from).toList();
    }

    @GetMapping("/{campaignId}")
    CampaignResponse get(@PathVariable UUID campaignId) {
        return CampaignResponse.from(campaigns.campaign(tenant(), campaignId));
    }

    @PostMapping("/{campaignId}/activate")
    CampaignResponse activate(@PathVariable UUID campaignId) {
        return CampaignResponse.from(campaigns.activate(tenant(), campaignId));
    }

    @PostMapping("/{campaignId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    CampaignService.PrepareResult prepare(@PathVariable UUID campaignId) {
        return campaigns.prepare(tenant(), campaignId);
    }

    @GetMapping("/{campaignId}/runs")
    List<RunResponse> runs(@PathVariable UUID campaignId) {
        return campaigns.runs(tenant(), campaignId).stream().map(RunResponse::from).toList();
    }

    @GetMapping("/runs/{runId}/recipients")
    List<RecipientResponse> recipients(@PathVariable UUID runId) {
        return campaigns.recipients(tenant(), runId).stream().map(RecipientResponse::from).toList();
    }

    @PostMapping("/runs/{runId}/eligibility-recheck")
    CampaignEligibilityService.EligibilityResult recheck(@PathVariable UUID runId) {
        campaigns.run(tenant(), runId);
        return eligibility.recheck(tenant(), runId);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    record CreateCampaignRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull UUID templateVersionId,
            @NotBlank @Size(max = 30) String channel,
            Instant scheduledAt,
            CampaignSelection selection) {}

    record CampaignResponse(
            UUID id,
            String name,
            CampaignStatus status,
            UUID templateVersionId,
            String channel,
            Instant scheduledAt) {
        static CampaignResponse from(Campaign value) {
            return new CampaignResponse(
                    value.getId(),
                    value.getName(),
                    value.getStatus(),
                    value.getTemplateVersionId(),
                    value.getChannel(),
                    value.getScheduledAt());
        }
    }

    record RunResponse(UUID id, UUID campaignId, CampaignRunStatus status) {
        static RunResponse from(CampaignRun value) {
            return new RunResponse(value.getId(), value.getCampaignId(), value.getStatus());
        }
    }

    record RecipientResponse(
            UUID id,
            UUID customerId,
            UUID invoiceId,
            String channel,
            String destination,
            String locale,
            CampaignRecipientStatus status,
            String skipReason) {
        static RecipientResponse from(CampaignRecipient value) {
            return new RecipientResponse(
                    value.getId(),
                    value.getCustomerId(),
                    value.getInvoiceId(),
                    value.getChannel(),
                    value.getDestination(),
                    value.getLocale(),
                    value.getStatus(),
                    value.getSkipReason());
        }
    }
}
