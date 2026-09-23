package io.collectra.api.platform.api;

import io.collectra.api.platform.application.PlatformOverviewQueryService;
import io.collectra.api.platform.application.PlatformOverviewQueryService.Overview;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/overview")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_SUPER_ADMIN')")
public class PlatformOverviewController {
    private final PlatformOverviewQueryService overview;

    public PlatformOverviewController(PlatformOverviewQueryService overview) {
        this.overview = overview;
    }

    @GetMapping
    Overview overview() {
        return overview.overview();
    }
}
