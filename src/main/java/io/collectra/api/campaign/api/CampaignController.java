package io.collectra.api.campaign.api;

import io.collectra.api.campaign.application.CampaignEligibilityService;
import io.collectra.api.campaign.application.CampaignFrontendQueryService;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.CampaignItem;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.PageResponse;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.RecipientItem;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.RunItem;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignStatus;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/campaigns")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CampaignController {
    private final CampaignService campaigns;
    private final CampaignEligibilityService eligibility;
    private final CampaignFrontendQueryService queries;

    public CampaignController(
            CampaignService campaigns,
            CampaignEligibilityService eligibility,
            CampaignFrontendQueryService queries) {
        this.campaigns = campaigns;
        this.eligibility = eligibility;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
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
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    PageResponse<CampaignItem> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant scheduledFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant scheduledTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.campaigns(
                tenant(),
                search,
                status,
                channel,
                scheduledFrom,
                scheduledTo,
                createdFrom,
                createdTo,
                page,
                size,
                sort);
    }

    @GetMapping("/{campaignId}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    CampaignResponse get(@PathVariable UUID campaignId) {
        return CampaignResponse.from(campaigns.campaign(tenant(), campaignId));
    }

    @PostMapping("/{campaignId}/activate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignResponse activate(@PathVariable UUID campaignId) {
        return CampaignResponse.from(campaigns.activate(tenant(), campaignId));
    }

    @PostMapping("/{campaignId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignService.PrepareResult prepare(@PathVariable UUID campaignId) {
        return campaigns.prepare(tenant(), campaignId);
    }

    @GetMapping("/{campaignId}/runs")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    PageResponse<RunItem> runs(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.runs(tenant(), campaignId, status, page, size);
    }

    @GetMapping("/{campaignId}/runs/{runId}/recipients")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    PageResponse<RecipientItem> recipients(
            @PathVariable UUID campaignId,
            @PathVariable UUID runId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queries.recipients(
                tenant(), campaignId, runId, status, channel, customerId, page, size);
    }

    @PostMapping("/runs/{runId}/eligibility-recheck")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
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
}
