package io.collectra.api.dashboard.api;

import io.collectra.api.dashboard.application.DashboardQueryService;
import io.collectra.api.shared.api.DecimalString;
import io.collectra.api.shared.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    public Summary summary() {
        return Summary.from(queries.summary(TenantContext.requireTenantId()));
    }

    @GetMapping("/receivables")
    public Receivables receivables() {
        return Receivables.from(queries.receivables(TenantContext.requireTenantId()));
    }

    @GetMapping("/delivery")
    public DashboardQueryService.Delivery delivery() {
        return queries.delivery(TenantContext.requireTenantId());
    }

    @GetMapping("/collections")
    public DashboardQueryService.Collections collections() {
        return queries.collections(TenantContext.requireTenantId());
    }

    public record CurrencyTotal(
            String currency,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amount) {
        static CurrencyTotal from(DashboardQueryService.CurrencyTotal value) {
            return new CurrencyTotal(value.currency(), DecimalString.of(value.amount()));
        }
    }

    public record Summary(
            Instant asOf,
            LocalDate businessDate,
            long customers,
            long activeContracts,
            long openCollectionCases,
            long activeCampaigns,
            List<CurrencyTotal> outstandingByCurrency) {
        static Summary from(DashboardQueryService.Summary value) {
            return new Summary(
                    value.asOf(),
                    value.businessDate(),
                    value.customers(),
                    value.activeContracts(),
                    value.openCollectionCases(),
                    value.activeCampaigns(),
                    value.outstandingByCurrency().stream().map(CurrencyTotal::from).toList());
        }
    }

    public record Aging(
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString current,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString days1To30,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString days31To60,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString days61To90,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString days90Plus) {
        static Aging from(DashboardQueryService.Aging value) {
            return new Aging(
                    DecimalString.of(value.current()),
                    DecimalString.of(value.days1To30()),
                    DecimalString.of(value.days31To60()),
                    DecimalString.of(value.days61To90()),
                    DecimalString.of(value.days90Plus()));
        }
    }

    public record CurrencyReceivables(
            String currency,
            @Schema(type = "string", pattern = DecimalString.PATTERN)
                    DecimalString outstanding,
            long dueToday,
            long dueSoon,
            Aging aging) {
        static CurrencyReceivables from(DashboardQueryService.CurrencyReceivables value) {
            return new CurrencyReceivables(
                    value.currency(),
                    DecimalString.of(value.outstanding()),
                    value.dueToday(),
                    value.dueSoon(),
                    Aging.from(value.aging()));
        }
    }

    public record Receivables(
            Instant asOf,
            LocalDate businessDate,
            List<CurrencyReceivables> currencies) {
        static Receivables from(DashboardQueryService.Receivables value) {
            return new Receivables(
                    value.asOf(),
                    value.businessDate(),
                    value.currencies().stream().map(CurrencyReceivables::from).toList());
        }
    }
}
