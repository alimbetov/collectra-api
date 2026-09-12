package io.collectra.api.customer.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.customer.application.CustomerQueryService;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CustomerController {
    private final CustomerService service;
    private final CustomerQueryService queries;

    public CustomerController(CustomerService service, CustomerQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping
    public CustomerQueryService.CustomerPage list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) CustomerType customerType,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) UUID segmentId,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(CustomerQueryService.MAX_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.customers(
                tenant(),
                search,
                status,
                customerType,
                managerId,
                segmentId,
                externalId,
                email,
                phone,
                createdFrom,
                createdTo,
                page,
                size,
                sort);
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
    public CustomerResponse status(
            @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        UUID tenantId = tenant();
        return response(tenantId, service.changeStatus(tenantId, id, request.status()));
    }

    @PostMapping("/{id}/emails")
    @ResponseStatus(HttpStatus.CREATED)
    public EmailResponse addEmail(
            @PathVariable UUID id, @Valid @RequestBody EmailCreateRequest request) {
        CustomerEmail value =
                service.addEmail(tenant(), id, request.email(), request.type(), request.primary());
        return EmailResponse.from(value);
    }

    @GetMapping("/{id}/emails")
    public List<EmailResponse> emails(@PathVariable UUID id) {
        return service.emails(tenant(), id).stream().map(EmailResponse::from).toList();
    }

    @PatchMapping("/{id}/emails/{emailId}")
    public EmailResponse updateEmail(
            @PathVariable UUID id,
            @PathVariable UUID emailId,
            @Valid @RequestBody ContactPatchRequest request) {
        return EmailResponse.from(
                service.updateEmail(
                        tenant(),
                        id,
                        emailId,
                        request.type(),
                        request.primary(),
                        request.status()));
    }

    @PostMapping("/{id}/phones")
    @ResponseStatus(HttpStatus.CREATED)
    public PhoneResponse addPhone(
            @PathVariable UUID id, @Valid @RequestBody PhoneCreateRequest request) {
        CustomerPhone value =
                service.addPhone(tenant(), id, request.phone(), request.type(), request.primary());
        return PhoneResponse.from(value);
    }

    @GetMapping("/{id}/phones")
    public List<PhoneResponse> phones(@PathVariable UUID id) {
        return service.phones(tenant(), id).stream().map(PhoneResponse::from).toList();
    }

    @PatchMapping("/{id}/phones/{phoneId}")
    public PhoneResponse updatePhone(
            @PathVariable UUID id,
            @PathVariable UUID phoneId,
            @Valid @RequestBody ContactPatchRequest request) {
        return PhoneResponse.from(
                service.updatePhone(
                        tenant(),
                        id,
                        phoneId,
                        request.type(),
                        request.primary(),
                        request.status()));
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
                service.segmentIds(tenantId, value.getId()),
                value.getCreatedAt(),
                value.getUpdatedAt(),
                value.getVersion());
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

    public record EmailCreateRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 20) String type,
            boolean primary) {}

    public record PhoneCreateRequest(
            @NotBlank @Size(max = 40) String phone, @Size(max = 20) String type, boolean primary) {}

    public record ContactPatchRequest(
            @Size(max = 20) String type,
            Boolean primary,
            @Pattern(regexp = "(?i)ACTIVE|INACTIVE") String status) {}

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
            List<UUID> segmentIds,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    public record EmailResponse(
            UUID id,
            String email,
            String type,
            boolean primary,
            boolean verified,
            String status,
            long version) {
        static EmailResponse from(CustomerEmail value) {
            return new EmailResponse(
                    value.getId(),
                    value.getEmail(),
                    value.getType(),
                    value.isPrimary(),
                    value.isVerified(),
                    value.getStatus(),
                    value.getVersion());
        }
    }

    public record PhoneResponse(
            UUID id,
            String phone,
            String normalizedPhone,
            String type,
            boolean primary,
            boolean verified,
            String status,
            long version) {
        static PhoneResponse from(CustomerPhone value) {
            return new PhoneResponse(
                    value.getId(),
                    value.getPhone(),
                    value.getNormalizedPhone(),
                    value.getType(),
                    value.isPrimary(),
                    value.isVerified(),
                    value.getStatus(),
                    value.getVersion());
        }
    }
}
