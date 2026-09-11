package io.collectra.api.customer.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerResponse> list() {
        UUID tenantId = tenant();
        return service.list(tenantId).stream().map(value -> response(tenantId, value)).toList();
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        UUID tenantId = tenant();
        return response(tenantId, service.get(tenantId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        UUID tenantId = tenant();
        Customer value =
                service.create(
                        tenantId,
                        request.externalId(),
                        request.customerType(),
                        request.displayName(),
                        request.firstName(),
                        request.lastName(),
                        request.middleName(),
                        request.companyName(),
                        request.managerUserId(),
                        request.preferredLocale(),
                        request.timezone(),
                        request.customFields());
        return response(tenantId, value);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(
            @PathVariable UUID id, @Valid @RequestBody CustomerUpdateRequest request) {
        UUID tenantId = tenant();
        Customer value =
                service.update(
                        tenantId,
                        id,
                        request.displayName(),
                        request.firstName(),
                        request.lastName(),
                        request.middleName(),
                        request.companyName(),
                        request.managerUserId(),
                        request.preferredLocale(),
                        request.timezone(),
                        request.customFields());
        return response(tenantId, value);
    }

    @PatchMapping("/{id}/status")
    public CustomerResponse status(@PathVariable UUID id, @RequestBody StatusRequest request) {
        UUID tenantId = tenant();
        return response(tenantId, service.changeStatus(tenantId, id, request.status()));
    }

    @PostMapping("/{id}/emails")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailResponse addEmail(
            @PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        CustomerEmail value =
                service.addEmail(tenant(), id, request.value(), request.type(), request.primary());
        return EmailResponse.from(value);
    }

    @GetMapping("/{id}/emails")
    public List<EmailResponse> emails(@PathVariable UUID id) {
        return service.emails(tenant(), id).stream().map(EmailResponse::from).toList();
    }

    @PostMapping("/{id}/phones")
    @ResponseStatus(HttpStatus.CREATED)
    public PhoneResponse addPhone(
            @PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        CustomerPhone value =
                service.addPhone(tenant(), id, request.value(), request.type(), request.primary());
        return PhoneResponse.from(value);
    }

    @GetMapping("/{id}/phones")
    public List<PhoneResponse> phones(@PathVariable UUID id) {
        return service.phones(tenant(), id).stream().map(PhoneResponse::from).toList();
    }

    @PostMapping("/{id}/segments/{segmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addSegment(@PathVariable UUID id, @PathVariable UUID segmentId) {
        service.addSegment(tenant(), id, segmentId);
    }

    @DeleteMapping("/{id}/segments/{segmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSegment(@PathVariable UUID id, @PathVariable UUID segmentId) {
        service.removeSegment(tenant(), id, segmentId);
    }

    private CustomerResponse response(UUID tenantId, Customer value) {
        return new CustomerResponse(
                value.getId(),
                value.getExternalId(),
                value.getCustomerType(),
                value.getDisplayName(),
                value.getFirstName(),
                value.getLastName(),
                value.getMiddleName(),
                value.getCompanyName(),
                value.getStatus(),
                value.getManagerUserId(),
                value.getPreferredLocale(),
                value.getTimezone(),
                value.getCustomFields(),
                service.segmentIds(tenantId, value.getId()));
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 120) String externalId,
            CustomerType customerType,
            @NotBlank @Size(max = 300) String displayName,
            @Size(max = 120) String firstName,
            @Size(max = 120) String lastName,
            @Size(max = 120) String middleName,
            @Size(max = 300) String companyName,
            UUID managerUserId,
            @Size(max = 35) String preferredLocale,
            @Size(max = 60) String timezone,
            JsonNode customFields) {}

    public record CustomerUpdateRequest(
            @NotBlank @Size(max = 300) String displayName,
            @Size(max = 120) String firstName,
            @Size(max = 120) String lastName,
            @Size(max = 120) String middleName,
            @Size(max = 300) String companyName,
            UUID managerUserId,
            @Size(max = 35) String preferredLocale,
            @Size(max = 60) String timezone,
            JsonNode customFields) {}

    public record StatusRequest(@NotNull CustomerStatus status) {}

    public record ContactRequest(
            @NotBlank String value, @Size(max = 20) String type, boolean primary) {}

    public record CustomerResponse(
            UUID id,
            String externalId,
            CustomerType customerType,
            String displayName,
            String firstName,
            String lastName,
            String middleName,
            String companyName,
            CustomerStatus status,
            UUID managerUserId,
            String preferredLocale,
            String timezone,
            JsonNode customFields,
            List<UUID> segmentIds) {}

    public record EmailResponse(
            UUID id, String email, String type, boolean primary, boolean verified, String status) {
        static EmailResponse from(CustomerEmail value) {
            return new EmailResponse(
                    value.getId(),
                    value.getEmail(),
                    value.getType(),
                    value.isPrimary(),
                    value.isVerified(),
                    value.getStatus());
        }
    }

    public record PhoneResponse(
            UUID id,
            String phone,
            String normalizedPhone,
            String type,
            boolean primary,
            boolean verified,
            String status) {
        static PhoneResponse from(CustomerPhone value) {
            return new PhoneResponse(
                    value.getId(),
                    value.getPhone(),
                    value.getNormalizedPhone(),
                    value.getType(),
                    value.isPrimary(),
                    value.isVerified(),
                    value.getStatus());
        }
    }
}
