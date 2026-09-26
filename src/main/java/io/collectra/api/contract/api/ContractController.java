package io.collectra.api.contract.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.contract.application.ContractQueryService;
import io.collectra.api.contract.application.ContractService;
import io.collectra.api.contract.domain.Contract;
import io.collectra.api.contract.domain.ContractStatus;
import io.collectra.api.shared.tenant.TenantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/contracts")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class ContractController {
    private final ContractService service;
    private final ContractQueryService queries;

    public ContractController(ContractService service, ContractQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_READ')")
    @GetMapping
    public ContractQueryService.ContractPage list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) LocalDate validFrom,
            @RequestParam(required = false) LocalDate validTo,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ContractQueryService.MAX_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.list(
                tenant(),
                search,
                customerId,
                status,
                externalId,
                validFrom,
                validTo,
                createdFrom,
                createdTo,
                page,
                size,
                sort);
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_READ')")
    @GetMapping("/{id}")
    public ContractResponse get(@PathVariable UUID id) {
        return ContractResponse.from(queries.detail(tenant(), id));
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_MANAGE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(@Valid @RequestBody CreateRequest request) {
        UUID tenantId = tenant();
        Contract value =
                service.create(
                        tenantId,
                        request.customerId(),
                        request.externalId(),
                        request.contractNumber(),
                        request.validFrom(),
                        request.validTo(),
                        request.renewalDate(),
                        request.customFields());
        return ContractResponse.from(queries.detail(tenantId, value.getId()));
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_MANAGE')")
    @PutMapping("/{id}")
    public ContractResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        UUID tenantId = tenant();
        service.update(
                tenantId,
                id,
                request.version(),
                request.contractNumber(),
                request.validFrom(),
                request.validTo(),
                request.renewalDate(),
                request.customFields());
        return ContractResponse.from(queries.detail(tenantId, id));
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_MANAGE')")
    @PostMapping("/{id}/suspend")
    public ContractResponse suspend(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return transition(id, request.version(), service::suspend);
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_MANAGE')")
    @PostMapping("/{id}/activate")
    public ContractResponse activate(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return transition(id, request.version(), service::activate);
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_MANAGE')")
    @PostMapping("/{id}/close")
    public ContractResponse close(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return transition(id, request.version(), service::close);
    }

    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('CONTRACT_MANAGE')")
    @PostMapping("/{id}/cancel")
    public ContractResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return transition(id, request.version(), service::cancel);
    }

    private ContractResponse transition(UUID id, long version, TransitionCommand command) {
        UUID tenantId = tenant();
        command.apply(tenantId, id, version);
        return ContractResponse.from(queries.detail(tenantId, id));
    }

    @FunctionalInterface
    private interface TransitionCommand {
        Contract apply(UUID tenantId, UUID id, long version);
    }

    private static UUID tenant() {
        return TenantContext.requireTenantId();
    }

    public record CreateRequest(
            @NotNull UUID customerId,
            @NotBlank @Size(max = 120) String externalId,
            @NotBlank @Size(max = 160) String contractNumber,
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {}

    @Schema(name = "ContractUpdateRequest")
    public record UpdateRequest(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 160) String contractNumber,
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {}

    @Schema(name = "ContractVersionRequest")
    public record VersionRequest(@NotNull @Min(0) Long version) {}

    public record ContractResponse(
            UUID id,
            UUID customerId,
            String customerExternalId,
            String customerDisplayName,
            String externalId,
            String contractNumber,
            ContractStatus status,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static ContractResponse from(ContractQueryService.ContractDetail value) {
            return new ContractResponse(
                    value.id(),
                    value.customerId(),
                    value.customerExternalId(),
                    value.customerDisplayName(),
                    value.externalId(),
                    value.contractNumber(),
                    value.status(),
                    value.validFrom(),
                    value.validTo(),
                    value.renewalDate(),
                    value.customFields(),
                    value.createdAt(),
                    value.updatedAt(),
                    value.version());
        }
    }
}
