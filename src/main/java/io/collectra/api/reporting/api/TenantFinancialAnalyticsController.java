package io.collectra.api.reporting.api;

import io.collectra.api.reporting.application.TenantFinancialAnalyticsQueryService;
import io.collectra.api.reporting.application.TenantFinancialAnalyticsQueryService.Bucket;
import io.collectra.api.shared.tenant.TenantContext;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/tenant")
@PreAuthorize(
        "hasAuthority('ROLE_HUMAN') and hasAuthority('RECEIVABLE_READ') and hasAuthority('COLLECTION_READ')")
public class TenantFinancialAnalyticsController {
    private final TenantFinancialAnalyticsQueryService analytics;

    public TenantFinancialAnalyticsController(TenantFinancialAnalyticsQueryService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public TenantFinancialAnalyticsQueryService.Report summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return analytics.summary(TenantContext.requireTenantId(), from, to);
    }

    @GetMapping("/timeseries")
    public TenantFinancialAnalyticsQueryService.TimeSeries timeseries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "DAY") Bucket bucket) {
        return analytics.timeseries(TenantContext.requireTenantId(), from, to, bucket);
    }
}
