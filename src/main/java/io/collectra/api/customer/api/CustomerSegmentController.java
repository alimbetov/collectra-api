package io.collectra.api.customer.api;

import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerSegment;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer-segments")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CustomerSegmentController {
    private final CustomerService service;
    public CustomerSegmentController(CustomerService service) { this.service = service; }
    @GetMapping public List<Response> list() { return service.segments(tenant()).stream().map(Response::from).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Response create(@Valid @RequestBody Request r) { return Response.from(service.createSegment(tenant(), r.code(), r.name(), r.description())); }
    private UUID tenant() { return TenantContext.requireTenantId(); }
    record Request(@NotBlank @Size(max=80) String code, @NotBlank @Size(max=200) String name, @Size(max=1000) String description) {}
    record Response(UUID id, String code, String name, String description, boolean active) { static Response from(CustomerSegment v) { return new Response(v.getId(), v.getCode(), v.getName(), v.getDescription(), v.isActive()); } }
}
