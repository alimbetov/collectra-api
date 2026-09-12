package io.collectra.api.dashboard.api;

import io.collectra.api.dashboard.application.DashboardQueryService;
import io.collectra.api.shared.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class DashboardController {
    private final DashboardQueryService queries;

    public DashboardController(DashboardQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/summary")
    public DashboardQueryService.Summary summary() {
        return queries.summary(TenantContext.requireTenantId());
    }

    @GetMapping("/receivables")
    public DashboardQueryService.Receivables receivables() {
        return queries.receivables(TenantContext.requireTenantId());
    }

    @GetMapping("/delivery")
    public DashboardQueryService.Delivery delivery() {
        return queries.delivery(TenantContext.requireTenantId());
    }

    @GetMapping("/collections")
    public DashboardQueryService.Collections collections() {
        return queries.collections(TenantContext.requireTenantId());
    }
}
