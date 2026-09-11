package io.collectra.api.customer.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.*;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }
    @GetMapping public List<CustomerResponse> list() { return service.list(tenant()).stream().map(this::response).toList(); }
    @GetMapping("/{id}") public CustomerResponse get(@PathVariable UUID id) { return response(service.get(tenant(), id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public CustomerResponse create(@Valid @RequestBody CustomerRequest r) { return response(service.create(tenant(), r.externalId(), r.customerType(), r.displayName(), r.firstName(), r.lastName(), r.middleName(), r.companyName(), r.managerUserId(), r.preferredLocale(), r.timezone(), r.customFields())); }
    @PutMapping("/{id}") public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerUpdateRequest r) { return response(service.update(tenant(), id, r.displayName(), r.firstName(), r.lastName(), r.middleName(), r.companyName(), r.managerUserId(), r.preferredLocale(), r.timezone(), r.customFields())); }
    @PatchMapping("/{id}/status") public CustomerResponse status(@PathVariable UUID id, @RequestBody StatusRequest r) { return response(service.changeStatus(tenant(), id, r.status())); }
    @PostMapping("/{id}/emails") @ResponseStatus(HttpStatus.CREATED) public EmailResponse addEmail(@PathVariable UUID id, @Valid @RequestBody ContactRequest r) { CustomerEmail v = service.addEmail(tenant(), id, r.value(), r.type(), r.primary()); return new EmailResponse(v.getId(), v.getEmail(), v.getType(), v.isPrimary(), v.isVerified(), v.getStatus()); }
    @GetMapping("/{id}/emails") public List<EmailResponse> emails(@PathVariable UUID id) { return service.emails(tenant(), id).stream().map(v -> new EmailResponse(v.getId(), v.getEmail(), v.getType(), v.isPrimary(), v.isVerified(), v.getStatus())).toList(); }
    @PostMapping("/{id}/phones") @ResponseStatus(HttpStatus.CREATED) public PhoneResponse addPhone(@PathVariable UUID id, @Valid @RequestBody ContactRequest r) { CustomerPhone v = service.addPhone(tenant(), id, r.value(), r.type(), r.primary()); return new PhoneResponse(v.getId(), v.getPhone(), v.getNormalizedPhone(), v.getType(), v.isPrimary(), v.isVerified(), v.getStatus()); }
    @GetMapping("/{id}/phones") public List<PhoneResponse> phones(@PathVariable UUID id) { return service.phones(tenant(), id).stream().map(v -> new PhoneResponse(v.getId(), v.getPhone(), v.getNormalizedPhone(), v.getType(), v.isPrimary(), v.isVerified(), v.getStatus())).toList(); }
    @PostMapping("/{id}/segments/{segmentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void addSegment(@PathVariable UUID id, @PathVariable UUID segmentId) { service.addSegment(tenant(), id, segmentId); }
    @DeleteMapping("/{id}/segments/{segmentId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeSegment(@PathVariable UUID id, @PathVariable UUID segmentId) { service.removeSegment(tenant(), id, segmentId); }
    private CustomerResponse response(Customer v) { return new CustomerResponse(v.getId(), v.getExternalId(), v.getCustomerType(), v.getDisplayName(), v.getFirstName(), v.getLastName(), v.getMiddleName(), v.getCompanyName(), v.getStatus(), v.getManagerUserId(), v.getPreferredLocale(), v.getTimezone(), v.getCustomFields(), service.segmentIds(tenant(), v.getId())); }
    private UUID tenant() { return TenantContext.requireTenantId(); }
    public record CustomerRequest(@NotBlank @Size(max=120) String externalId, CustomerType customerType, @NotBlank @Size(max=300) String displayName, @Size(max=120) String firstName, @Size(max=120) String lastName, @Size(max=120) String middleName, @Size(max=300) String companyName, UUID managerUserId, @Size(max=35) String preferredLocale, @Size(max=60) String timezone, JsonNode customFields) {}
    public record CustomerUpdateRequest(@NotBlank @Size(max=300) String displayName, @Size(max=120) String firstName, @Size(max=120) String lastName, @Size(max=120) String middleName, @Size(max=300) String companyName, UUID managerUserId, @Size(max=35) String preferredLocale, @Size(max=60) String timezone, JsonNode customFields) {}
    public record StatusRequest(@NotNull CustomerStatus status) {}
    public record ContactRequest(@NotBlank String value, @Size(max=20) String type, boolean primary) {}
    public record CustomerResponse(UUID id, String externalId, CustomerType customerType, String displayName, String firstName, String lastName, String middleName, String companyName, CustomerStatus status, UUID managerUserId, String preferredLocale, String timezone, JsonNode customFields, List<UUID> segmentIds) {}
    public record EmailResponse(UUID id, String email, String type, boolean primary, boolean verified, String status) {}
    public record PhoneResponse(UUID id, String phone, String normalizedPhone, String type, boolean primary, boolean verified, String status) {}
}
