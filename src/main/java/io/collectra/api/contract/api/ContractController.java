package io.collectra.api.contract.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.contract.application.ContractQueryService;
import io.collectra.api.contract.application.ContractService;
import io.collectra.api.contract.domain.Contract;
import io.collectra.api.contract.domain.ContractStatus;
import io.collectra.api.shared.tenant.TenantContext;
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

    @GetMapping("/{id}")
    public ContractResponse get(@PathVariable UUID id) {
        return ContractResponse.from(service.get(tenant(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(@Valid @RequestBody CreateRequest request) {
        return ContractResponse.from(
                service.create(
                        tenant(),
                        request.customerId(),
                        request.externalId(),
                        request.contractNumber(),
                        request.validFrom(),
                        request.validTo(),
                        request.renewalDate(),
                        request.customFields()));
    }

    @PutMapping("/{id}")
    public ContractResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return ContractResponse.from(
                service.update(
                        tenant(),
                        id,
                        request.version(),
                        request.contractNumber(),
                        request.validFrom(),
                        request.validTo(),
                        request.renewalDate(),
                        request.customFields()));
    }

    @PostMapping("/{id}/suspend")
    public ContractResponse suspend(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ContractResponse.from(service.suspend(tenant(), id, request.version()));
    }

    @PostMapping("/{id}/activate")
    public ContractResponse activate(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ContractResponse.from(service.activate(tenant(), id, request.version()));
    }

    @PostMapping("/{id}/close")
    public ContractResponse close(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ContractResponse.from(service.close(tenant(), id, request.version()));
    }

    @PostMapping("/{id}/cancel")
    public ContractResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        return ContractResponse.from(service.cancel(tenant(), id, request.version()));
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

    public record UpdateRequest(
            @Min(0) long version,
            @NotBlank @Size(max = 160) String contractNumber,
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {}

    public record VersionRequest(@Min(0) long version) {}

    public record ContractResponse(
            UUID id,
            UUID customerId,
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
        static ContractResponse from(Contract value) {
            return new ContractResponse(
                    value.getId(),
                    value.getCustomerId(),
                    value.getExternalId(),
                    value.getContractNumber(),
                    value.getStatus(),
                    value.getValidFrom(),
                    value.getValidTo(),
                    value.getRenewalDate(),
                    value.getCustomFields(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }
}
