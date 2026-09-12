package io.collectra.api.customer.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import io.collectra.api.customer.domain.CustomerSegment;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.customer.infrastructure.CustomerEmailRepository;
import io.collectra.api.customer.infrastructure.CustomerPhoneRepository;
import io.collectra.api.customer.infrastructure.CustomerRepository;
import io.collectra.api.customer.infrastructure.CustomerSegmentMemberRepository;
import io.collectra.api.customer.infrastructure.CustomerSegmentRepository;
import io.collectra.api.shared.error.BusinessConflictException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final CustomerEmailRepository emails;
    private final CustomerPhoneRepository phones;
    private final CustomerSegmentRepository segments;
    private final CustomerSegmentMemberRepository members;

    public CustomerService(
            CustomerRepository customers,
            CustomerEmailRepository emails,
            CustomerPhoneRepository phones,
            CustomerSegmentRepository segments,
            CustomerSegmentMemberRepository members) {
        this.customers = customers;
        this.emails = emails;
        this.phones = phones;
        this.segments = segments;
        this.members = members;
    }

    @Transactional
    public Customer create(
            UUID tenantId,
            String externalId,
            CustomerType type,
            String displayName,
            String firstName,
            String lastName,
            String middleName,
            String companyName,
            UUID managerUserId,
            String locale,
            String timezone,
            JsonNode customFields) {
        customers
                .findByTenantIdAndExternalId(tenantId, externalId.trim())
                .ifPresent(
                        value -> {
                            throw new BusinessConflictException(
                                    "DUPLICATE_EXTERNAL_ID",
                                    "Customer externalId already exists");
                        });
        return customers.save(
                new Customer(
                        tenantId,
                        externalId,
                        type,
                        displayName,
                        firstName,
                        lastName,
                        middleName,
                        companyName,
                        managerUserId,
                        locale,
                        timezone,
                        customFields));
    }

    @Transactional(readOnly = true)
    public Customer get(UUID tenantId, UUID id) {
        return customers
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByExternalId(UUID tenantId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return customers.findByTenantIdAndExternalId(tenantId, externalId.trim());
    }

    @Transactional(readOnly = true)
    public List<Customer> list(UUID tenantId) {
        return customers.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Customer> customersByIds(UUID tenantId, Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return customers.findAllByTenantIdAndIdIn(tenantId, customerIds);
    }

    @Transactional
    public Customer update(
            UUID tenantId,
            UUID id,
            String displayName,
            String firstName,
            String lastName,
            String middleName,
            String companyName,
            UUID managerUserId,
            String locale,
            String timezone,
            JsonNode customFields) {
        Customer value = get(tenantId, id);
        value.update(
                displayName,
                firstName,
                lastName,
                middleName,
                companyName,
                managerUserId,
                locale,
                timezone,
                customFields);
        return value;
    }

    @Transactional
    public Customer changeStatus(UUID tenantId, UUID id, CustomerStatus status) {
        Customer value = get(tenantId, id);
        value.changeStatus(status);
        return value;
    }

    @Transactional
    public CustomerEmail addEmail(
            UUID tenantId, UUID customerId, String email, String type, boolean primary) {
        get(tenantId, customerId);
        String normalized = CustomerEmail.normalizeForLookup(email);
        if (emails.existsByTenantIdAndCustomerIdAndEmail(tenantId, customerId, normalized)) {
            throw new BusinessConflictException("DUPLICATE_CONTACT", "Customer email already exists");
        }
        if (emails.countByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE") >= 5) {
            throw new BusinessConflictException(
                    "CONTACT_LIMIT_REACHED", "Customer email limit reached");
        }
        if (primary) {
            demoteEmailPrimaries(tenantId, customerId, null);
        }
        return emails.save(new CustomerEmail(tenantId, customerId, normalized, type, primary));
    }

    @Transactional
    public CustomerEmail updateEmail(
            UUID tenantId,
            UUID customerId,
            UUID emailId,
            String type,
            Boolean primary,
            String status) {
        get(tenantId, customerId);
        CustomerEmail value =
                emails.findByIdAndTenantIdAndCustomerId(emailId, tenantId, customerId)
                        .orElseThrow(() -> new NoSuchElementException("Customer email not found"));
        validatePrimaryState(primary, status, "email");
        if (Boolean.TRUE.equals(primary)) {
            demoteEmailPrimaries(tenantId, customerId, emailId);
        }
        value.updateMetadata(type, primary, status);
        return value;
    }

    @Transactional
    public CustomerPhone addPhone(
            UUID tenantId, UUID customerId, String phone, String type, boolean primary) {
        get(tenantId, customerId);
        String normalized = CustomerPhone.normalizeForLookup(phone);
        if (phones.existsByTenantIdAndCustomerIdAndNormalizedPhone(
                tenantId, customerId, normalized)) {
            throw new BusinessConflictException("DUPLICATE_CONTACT", "Customer phone already exists");
        }
        if (phones.countByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE") >= 2) {
            throw new BusinessConflictException(
                    "CONTACT_LIMIT_REACHED", "Customer phone limit reached");
        }
        if (primary) {
            demotePhonePrimaries(tenantId, customerId, null);
        }
        return phones.save(new CustomerPhone(tenantId, customerId, phone, type, primary));
    }

    @Transactional
    public CustomerPhone updatePhone(
            UUID tenantId,
            UUID customerId,
            UUID phoneId,
            String type,
            Boolean primary,
            String status) {
        get(tenantId, customerId);
        CustomerPhone value =
                phones.findByIdAndTenantIdAndCustomerId(phoneId, tenantId, customerId)
                        .orElseThrow(() -> new NoSuchElementException("Customer phone not found"));
        validatePrimaryState(primary, status, "phone");
        if (Boolean.TRUE.equals(primary)) {
            demotePhonePrimaries(tenantId, customerId, phoneId);
        }
        value.updateMetadata(type, primary, status);
        return value;
    }

    @Transactional(readOnly = true)
    public List<CustomerEmail> emails(UUID tenantId, UUID customerId) {
        get(tenantId, customerId);
        return emails.findAllByTenantIdAndCustomerId(tenantId, customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerEmail> emailsByCustomerIds(UUID tenantId, Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return emails.findAllByTenantIdAndCustomerIdIn(tenantId, customerIds);
    }

    @Transactional(readOnly = true)
    public List<CustomerPhone> phones(UUID tenantId, UUID customerId) {
        get(tenantId, customerId);
        return phones.findAllByTenantIdAndCustomerId(tenantId, customerId);
    }

    @Transactional
    public CustomerSegment createSegment(
            UUID tenantId, String code, String name, String description) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        segments.findByTenantIdAndCode(tenantId, normalizedCode)
                .ifPresent(
                        value -> {
                            throw new BusinessConflictException(
                                    "DUPLICATE_SEGMENT_CODE", "Customer segment code already exists");
                        });
        return segments.save(new CustomerSegment(tenantId, normalizedCode, name, description));
    }

    @Transactional(readOnly = true)
    public CustomerSegment segment(UUID tenantId, UUID segmentId) {
        return segments.findByIdAndTenantId(segmentId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Segment not found"));
    }

    @Transactional
    public CustomerSegment updateSegment(
            UUID tenantId,
            UUID segmentId,
            String name,
            String description,
            Boolean active,
            Long version) {
        CustomerSegment value = segment(tenantId, segmentId);
        if (version != null && value.getVersion() != version) {
            throw new BusinessConflictException("VERSION_CONFLICT", "Segment version conflict");
        }
        value.update(name, description, active);
        return value;
    }

    @Transactional(readOnly = true)
    public List<CustomerSegment> segments(UUID tenantId) {
        return segments.findAllByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional
    public void addSegment(UUID tenantId, UUID customerId, UUID segmentId) {
        get(tenantId, customerId);
        segment(tenantId, segmentId);
        if (!members.existsByTenantIdAndCustomerIdAndSegmentId(tenantId, customerId, segmentId)) {
            members.save(new CustomerSegmentMember(tenantId, customerId, segmentId));
        }
    }

    @Transactional
    public void removeSegment(UUID tenantId, UUID customerId, UUID segmentId) {
        get(tenantId, customerId);
        members.deleteByTenantIdAndCustomerIdAndSegmentId(tenantId, customerId, segmentId);
    }

    @Transactional(readOnly = true)
    public List<UUID> segmentIds(UUID tenantId, UUID customerId) {
        get(tenantId, customerId);
        return members.findAllByTenantIdAndCustomerId(tenantId, customerId).stream()
                .map(CustomerSegmentMember::getSegmentId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerSegmentMember> segmentMembershipsByCustomerIds(
            UUID tenantId, Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return members.findAllByTenantIdAndCustomerIdIn(tenantId, customerIds);
    }

    private void demoteEmailPrimaries(UUID tenantId, UUID customerId, UUID exceptId) {
        emails.findAllByTenantIdAndCustomerIdAndPrimaryTrueAndStatus(
                        tenantId, customerId, "ACTIVE")
                .stream()
                .filter(value -> exceptId == null || !value.getId().equals(exceptId))
                .forEach(CustomerEmail::demotePrimary);
    }

    private void demotePhonePrimaries(UUID tenantId, UUID customerId, UUID exceptId) {
        phones.findAllByTenantIdAndCustomerIdAndPrimaryTrueAndStatus(
                        tenantId, customerId, "ACTIVE")
                .stream()
                .filter(value -> exceptId == null || !value.getId().equals(exceptId))
                .forEach(CustomerPhone::demotePrimary);
    }

    private static void validatePrimaryState(Boolean primary, String status, String resource) {
        if (Boolean.TRUE.equals(primary)
                && status != null
                && "INACTIVE".equals(status.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessConflictException(
                    "INVALID_STATE_TRANSITION", "Inactive " + resource + " cannot be primary");
        }
    }
}
