package io.collectra.api.campaign.api;

import io.collectra.api.campaign.application.AudienceSelectionType;
import io.collectra.api.campaign.application.CampaignEligibilityService;
import io.collectra.api.campaign.application.CampaignFrontendQueryService;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.CampaignItem;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.PageResponse;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.RecipientItem;
import io.collectra.api.campaign.application.CampaignFrontendQueryService.RunItem;
import io.collectra.api.campaign.application.CampaignPreviewService;
import io.collectra.api.campaign.application.CampaignSelection;
import io.collectra.api.campaign.application.CampaignService;
import io.collectra.api.campaign.domain.Campaign;
import io.collectra.api.campaign.domain.CampaignStatus;
import io.collectra.api.shared.api.DecimalString;
import io.collectra.api.shared.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/campaigns")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CampaignController {
    private final CampaignService campaigns;
    private final CampaignEligibilityService eligibility;
    private final CampaignFrontendQueryService queries;
    private final CampaignPreviewService preview;

    public CampaignController(
            CampaignService campaigns,
            CampaignEligibilityService eligibility,
            CampaignFrontendQueryService queries,
            CampaignPreviewService preview) {
        this.campaigns = campaigns;
        this.eligibility = eligibility;
        this.queries = queries;
        this.preview = preview;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignResponse create(
            @Valid @RequestBody CreateCampaignRequest request, Authentication authentication) {
        CampaignSelection selection =
                request.selection() == null
                        ? CampaignSelection.empty(request.audienceSelectionType())
                        : request.selection().toApplication(request.audienceSelectionType());
        Campaign value =
                campaigns.create(
                        tenant(),
                        request.name(),
                        request.templateVersionId(),
                        request.channel(),
                        request.scheduledAt(),
                        selection,
                        request.documentTemplateVersionId(),
                        Boolean.TRUE.equals(request.generatedPdfLink()),
                        actor(authentication));
        return response(value);
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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
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
        return response(campaigns.campaign(tenant(), campaignId));
    }

    @PutMapping("/{campaignId}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignResponse update(
            @PathVariable UUID campaignId, @Valid @RequestBody UpdateCampaignRequest request) {
        CampaignSelection selection =
                request.selection() == null
                        ? CampaignSelection.empty(request.audienceSelectionType())
                        : request.selection().toApplication(request.audienceSelectionType());
        Campaign value =
                campaigns.updateDraft(
                        tenant(),
                        campaignId,
                        request.name(),
                        request.templateVersionId(),
                        request.channel(),
                        request.scheduledAt(),
                        selection,
                        request.documentTemplateVersionId(),
                        Boolean.TRUE.equals(request.generatedPdfLink()),
                        request.revision());
        return response(value);
    }

    @PostMapping("/{campaignId}/validate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignService.ValidationResult validate(@PathVariable UUID campaignId) {
        return campaigns.validate(tenant(), campaignId);
    }

    @PostMapping("/{campaignId}/preview")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignPreviewService.PreviewResult preview(
            @PathVariable UUID campaignId,
            @Valid @RequestBody(required = false) PreviewRequest request) {
        return preview.preview(
                tenant(),
                campaignId,
                request == null ? null : request.customerId(),
                request == null ? null : request.invoiceId());
    }

    @PostMapping("/{campaignId}/activate")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignResponse activate(
            @PathVariable UUID campaignId, @RequestParam @Min(0) long revision) {
        return response(campaigns.activate(tenant(), campaignId, revision));
    }

    @PostMapping("/{campaignId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignService.PrepareResult prepare(
            @PathVariable UUID campaignId,
            @RequestHeader("X-Command-Id") UUID commandId) {
        return campaigns.prepare(tenant(), campaignId, commandId);
    }

    @GetMapping("/{campaignId}/runs")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    PageResponse<RunItem> runs(
            @PathVariable UUID campaignId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.runs(tenant(), campaignId, status, page, size);
    }

    @GetMapping("/{campaignId}/runs/{runId}")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    RunItem run(@PathVariable UUID campaignId, @PathVariable UUID runId) {
        return queries.run(tenant(), campaignId, runId);
    }

    @GetMapping("/{campaignId}/runs/{runId}/recipients")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
    PageResponse<RecipientItem> recipients(
            @PathVariable UUID campaignId,
            @PathVariable UUID runId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return queries.recipients(
                tenant(), campaignId, runId, status, channel, customerId, page, size);
    }

    @PostMapping("/runs/{runId}/eligibility-recheck")
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_MANAGE')")
    CampaignEligibilityService.EligibilityResult recheck(@PathVariable UUID runId) {
        campaigns.run(tenant(), runId);
        return eligibility.recheck(tenant(), runId);
    }

    private CampaignResponse response(Campaign value) {
        return CampaignResponse.from(value, campaigns.selection(value));
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    private UUID actor(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    record CreateCampaignRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull UUID templateVersionId,
            @NotBlank @Size(max = 30) String channel,
            Instant scheduledAt,
            AudienceSelectionType audienceSelectionType,
            CampaignSelectionRequest selection,
            UUID documentTemplateVersionId,
            Boolean generatedPdfLink) {}

    record UpdateCampaignRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull UUID templateVersionId,
            @NotBlank @Size(max = 30) String channel,
            Instant scheduledAt,
            AudienceSelectionType audienceSelectionType,
            CampaignSelectionRequest selection,
            UUID documentTemplateVersionId,
            Boolean generatedPdfLink,
            @NotNull @Min(0) Long revision) {}

    record PreviewRequest(UUID customerId, UUID invoiceId) {}

    @Schema(name = "CampaignSelection")
    record CampaignSelectionRequest(
            Set<UUID> customerIds,
            Set<UUID> segmentIds,
            Integer daysOverdueFrom,
            Integer daysOverdueTo,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amountFrom,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amountTo) {
        CampaignSelection toApplication(AudienceSelectionType audienceSelectionType) {
            return new CampaignSelection(
                    customerIds,
                    segmentIds,
                    daysOverdueFrom,
                    daysOverdueTo,
                    amountFrom == null ? null : amountFrom.value(),
                    amountTo == null ? null : amountTo.value(),
                    audienceSelectionType);
        }
    }

    record CampaignSelectionResponse(
            Set<UUID> customerIds,
            Set<UUID> segmentIds,
            Integer daysOverdueFrom,
            Integer daysOverdueTo,
            DecimalString amountFrom,
            DecimalString amountTo) {
        static CampaignSelectionResponse from(CampaignSelection value) {
            return new CampaignSelectionResponse(
                    value.customerIds(),
                    value.segmentIds(),
                    value.daysOverdueFrom(),
                    value.daysOverdueTo(),
                    value.amountFrom() == null ? null : DecimalString.of(value.amountFrom()),
                    value.amountTo() == null ? null : DecimalString.of(value.amountTo()));
        }
    }

    record CampaignResponse(
            UUID id,
            String name,
            CampaignStatus status,
            String channel,
            AudienceSelectionType audienceSelectionType,
            CampaignSelectionResponse selection,
            UUID messageTemplateVersionId,
            UUID documentTemplateVersionId,
            boolean generatedPdfLink,
            boolean generatedPdfAttachment,
            boolean generatedPdfAttachmentRequired,
            Instant scheduledAt,
            Instant createdAt,
            Instant updatedAt,
            long revision) {
        static CampaignResponse from(Campaign value, CampaignSelection selection) {
            return new CampaignResponse(
                    value.getId(),
                    value.getName(),
                    value.getStatus(),
                    value.getChannel(),
                    selection.audienceSelectionType(),
                    CampaignSelectionResponse.from(selection),
                    value.getTemplateVersionId(),
                    value.getDocumentTemplateVersionId(),
                    value.isGeneratedPdfLink(),
                    value.isGeneratedPdfAttachment(),
                    value.isGeneratedPdfAttachmentRequired(),
                    value.getScheduledAt(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }
}
