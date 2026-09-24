package io.collectra.api.reporting.api;

import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService.Bucket;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService.Filter;
import io.collectra.api.reporting.application.CommunicationAnalyticsQueryService.Scope;
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
@RequestMapping("/api/v1/platform/analytics/communication")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class PlatformCommunicationAnalyticsController {
    private final CommunicationAnalyticsQueryService analytics;

    public PlatformCommunicationAnalyticsController(CommunicationAnalyticsQueryService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public CommunicationAnalyticsQueryService.Summary summary(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel) {
        return analytics.summary(
                scope(tenantId, userId), filter(from, to, campaignId, runId, channel));
    }

    @GetMapping("/timeseries")
    public CommunicationAnalyticsQueryService.TimeSeries timeseries(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "DAY") Bucket bucket) {
        return analytics.timeseries(
                scope(tenantId, userId),
                filter(from, to, campaignId, runId, channel),
                bucket);
    }

    @GetMapping("/channels")
    public CommunicationAnalyticsQueryService.ChannelReport channels(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel) {
        return analytics.channels(
                scope(tenantId, userId), filter(from, to, campaignId, runId, channel));
    }

    @GetMapping("/users")
    public CommunicationAnalyticsQueryService.PageReport<
                    CommunicationAnalyticsQueryService.UserItem>
            users(
                    @RequestParam(required = false) UUID tenantId,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "sent,desc") String sort) {
        return analytics.users(
                scope(tenantId, userId),
                filter(from, to, campaignId, runId, channel),
                page,
                size,
                sort);
    }

    @GetMapping("/campaigns")
    public CommunicationAnalyticsQueryService.PageReport<
                    CommunicationAnalyticsQueryService.CampaignItem>
            campaigns(
                    @RequestParam(required = false) UUID tenantId,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "lastRunAt,desc") String sort) {
        return analytics.campaigns(
                scope(tenantId, userId),
                filter(from, to, campaignId, runId, channel),
                page,
                size,
                sort);
    }

    @GetMapping("/failures")
    public CommunicationAnalyticsQueryService.PageReport<
                    CommunicationAnalyticsQueryService.FailureItem>
            failures(
                    @RequestParam(required = false) UUID tenantId,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "count,desc") String sort) {
        return analytics.failures(
                scope(tenantId, userId),
                filter(from, to, campaignId, runId, channel),
                page,
                size,
                sort);
    }

    @GetMapping("/documents")
    public CommunicationAnalyticsQueryService.DocumentReport documents(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String channel) {
        return analytics.documents(
                scope(tenantId, userId), filter(from, to, campaignId, runId, channel));
    }

    @GetMapping("/tenants")
    public CommunicationAnalyticsQueryService.PageReport<
                    CommunicationAnalyticsQueryService.TenantItem>
            tenants(
                    @RequestParam(required = false) UUID tenantId,
                    @RequestParam(required = false) UUID userId,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant from,
                    @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                            Instant to,
                    @RequestParam(required = false) UUID campaignId,
                    @RequestParam(required = false) UUID runId,
                    @RequestParam(required = false) String channel,
                    @RequestParam(defaultValue = "0") @Min(0) int page,
                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
                    @RequestParam(defaultValue = "sent,desc") String sort) {
        return analytics.tenants(
                scope(tenantId, userId),
                filter(from, to, campaignId, runId, channel),
                page,
                size,
                sort);
    }

    private Scope scope(UUID tenantId, UUID userId) {
        return Scope.platform(tenantId, userId);
    }

    private Filter filter(
            Instant from, Instant to, UUID campaignId, UUID runId, String channel) {
        return new Filter(from, to, campaignId, runId, channel, null);
    }
}
