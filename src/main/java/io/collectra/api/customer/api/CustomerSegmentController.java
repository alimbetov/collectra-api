package io.collectra.api.customer.api;

import io.collectra.api.customer.application.CustomerQueryService;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerSegment;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/customer-segments")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CustomerSegmentController {
    private final CustomerService service;
    private final CustomerQueryService queries;

    public CustomerSegmentController(CustomerService service, CustomerQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public CustomerQueryService.SegmentPage list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(CustomerQueryService.MAX_SIZE)
                    int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return queries.segments(tenant(), search, active, page, size, sort);
    }

    @GetMapping("/{segmentId}")
    public Response get(@PathVariable UUID segmentId) {
        return Response.from(service.segment(tenant(), segmentId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody CreateRequest request) {
        return Response.from(
                service.createSegment(
                        tenant(), request.code(), request.name(), request.description()));
    }

    @PatchMapping("/{segmentId}")
    public Response update(
            @PathVariable UUID segmentId, @Valid @RequestBody UpdateRequest request) {
        return Response.from(
                service.updateSegment(
                        tenant(),
                        segmentId,
                        request.name(),
                        request.description(),
                        request.active(),
                        request.version()));
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    public record CreateRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description) {}

    public record UpdateRequest(
            @Size(max = 200) String name,
            @Size(max = 1000) String description,
            Boolean active,
            Long version) {}

    public record Response(
            UUID id,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static Response from(CustomerSegment value) {
            return new Response(
                    value.getId(),
                    value.getCode(),
                    value.getName(),
                    value.getDescription(),
                    value.isActive(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }
}
