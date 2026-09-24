package io.collectra.api.reporting.api;

import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService.Bucket;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService.Filter;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService.Scope;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/analytics/communication")
@PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CAMPAIGN_READ')")
public class TenantCommunicationAnalyticsController {
    private final CommunicationAnalyticsQueryService analytics;

    public TenantCommunicationAnalyticsController(CommunicationAnalyticsQueryService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public CommunicationAnalyticsQueryService.CommunicationSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID userId) {
        return analytics.summary(scope(), filter(from, to, campaignId, runId, channel, userId));
    }

    @GetMapping("/timeseries")
    public CommunicationAnalyticsQueryService.CommunicationTimeSeries timeseries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "DAY") Bucket bucket) {
        return analytics.timeseries(
                scope(), filter(from, to, campaignId, runId, channel, userId), bucket);
    }

    @GetMapping("/channels")
    public CommunicationAnalyticsQueryService.CommunicationChannelReport channels(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID userId) {
        return analytics.channels(scope(), filter(from, to, campaignId, runId, channel, userId));
    }

    @GetMapping("/users")
    public CommunicationAnalyticsQueryService.CommunicationPageReport<
                    CommunicationAnalyticsQueryService.CommunicationUserItem>
            users(
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "sent,desc") String sort) {
        return analytics.users(
                scope(), filter(from, to, campaignId, runId, channel, userId), page, size, sort);
    }

    @GetMapping("/campaigns")
    public CommunicationAnalyticsQueryService.CommunicationPageReport<
                    CommunicationAnalyticsQueryService.CommunicationCampaignItem>
            campaigns(
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "lastRunAt,desc") String sort) {
        return analytics.campaigns(
                scope(), filter(from, to, campaignId, runId, channel, userId), page, size, sort);
    }

    @GetMapping("/failures")
    public CommunicationAnalyticsQueryService.CommunicationPageReport<
                    CommunicationAnalyticsQueryService.CommunicationFailureItem>
            failures(
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "count,desc") String sort) {
        return analytics.failures(
                scope(), filter(from, to, campaignId, runId, channel, userId), page, size, sort);
    }

    @GetMapping("/documents")
    public CommunicationAnalyticsQueryService.CommunicationDocumentReport documents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) UUID userId) {
        return analytics.documents(scope(), filter(from, to, campaignId, runId, channel, userId));
    }

    private Scope scope() {
        return Scope.tenant(TenantContext.requireTenantId());
    }

    private Filter filter(
            Instant from, Instant to, UUID campaignId, UUID runId, String channel, UUID userId) {
        return new Filter(from, to, campaignId, runId, channel, userId);
    }
}
