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
import java.util.Collection;
import java.util.List;
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
                            throw new IllegalArgumentException(
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
        if (emails.countByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE") >= 5) {
            throw new IllegalStateException("Customer email limit reached");
        }
        return emails.save(new CustomerEmail(tenantId, customerId, email, type, primary));
    }

    @Transactional
    public CustomerPhone addPhone(
            UUID tenantId, UUID customerId, String phone, String type, boolean primary) {
        get(tenantId, customerId);
        if (phones.countByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE") >= 2) {
            throw new IllegalStateException("Customer phone limit reached");
        }
        return phones.save(new CustomerPhone(tenantId, customerId, phone, type, primary));
    }

    @Transactional(readOnly = true)
    public List<CustomerEmail> emails(UUID tenantId, UUID customerId) {
        get(tenantId, customerId);
        return emails.findAllByTenantIdAndCustomerId(tenantId, customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerEmail> emailsByCustomerIds(
            UUID tenantId, Collection<UUID> customerIds) {
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
        return segments.save(new CustomerSegment(tenantId, code, name, description));
    }

    @Transactional(readOnly = true)
    public List<CustomerSegment> segments(UUID tenantId) {
        return segments.findAllByTenantIdOrderByNameAsc(tenantId);
    }

    @Transactional
    public void addSegment(UUID tenantId, UUID customerId, UUID segmentId) {
        get(tenantId, customerId);
        segments.findByIdAndTenantId(segmentId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Segment not found"));
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
}
