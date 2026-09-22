package io.collectra.api.customer.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerChannelAddress;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import io.collectra.api.customer.domain.CustomerSegment;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.customer.infrastructure.CustomerChannelAddressRepository;
import io.collectra.api.customer.infrastructure.CustomerEmailRepository;
import io.collectra.api.customer.infrastructure.CustomerPhoneRepository;
import io.collectra.api.customer.infrastructure.CustomerRepository;
import io.collectra.api.customer.infrastructure.CustomerSegmentMemberRepository;
import io.collectra.api.customer.infrastructure.CustomerSegmentRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import io.collectra.api.shared.error.BusinessConflictException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final CustomerChannelAddressRepository channelAddresses;
    private final CustomerEmailRepository emails;
    private final CustomerPhoneRepository phones;
    private final CustomerSegmentRepository segments;
    private final CustomerSegmentMemberRepository members;
    private final TenantMembershipRepository memberships;

    public CustomerService(
            CustomerRepository customers,
            CustomerChannelAddressRepository channelAddresses,
            CustomerEmailRepository emails,
            CustomerPhoneRepository phones,
            CustomerSegmentRepository segments,
            CustomerSegmentMemberRepository members,
            TenantMembershipRepository memberships) {
        this.customers = customers;
        this.channelAddresses = channelAddresses;
        this.emails = emails;
        this.phones = phones;
        this.segments = segments;
        this.members = members;
        this.memberships = memberships;
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
                                    "DUPLICATE_EXTERNAL_ID", "Customer externalId already exists");
                        });
        requireActiveManager(tenantId, managerUserId);
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

    @Transactional(readOnly = true)
    public Page<Customer> campaignAudienceCandidates(
            UUID tenantId,
            Collection<UUID> customerIds,
            Collection<UUID> segmentIds,
            Pageable pageable) {
        Collection<UUID> effectiveCustomerIds =
                customerIds == null ? List.of() : List.copyOf(customerIds);
        Collection<UUID> effectiveSegmentIds =
                segmentIds == null ? List.of() : List.copyOf(segmentIds);

        Specification<Customer> specification =
                (root, query, criteriaBuilder) -> {
                    java.util.ArrayList<jakarta.persistence.criteria.Predicate> predicates =
                            new java.util.ArrayList<>();
                    predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
                    predicates.add(
                            criteriaBuilder.equal(root.get("status"), CustomerStatus.ACTIVE));

                    if (!effectiveCustomerIds.isEmpty()) {
                        predicates.add(root.get("id").in(effectiveCustomerIds));
                    }

                    if (!effectiveSegmentIds.isEmpty()) {
                        var membership = query.subquery(Integer.class);
                        var member = membership.from(CustomerSegmentMember.class);
                        membership.select(criteriaBuilder.literal(1));
                        membership.where(
                                criteriaBuilder.equal(member.get("tenantId"), tenantId),
                                criteriaBuilder.equal(member.get("customerId"), root.get("id")),
                                member.get("segmentId").in(effectiveSegmentIds));
                        predicates.add(criteriaBuilder.exists(membership));
                    }

                    return criteriaBuilder.and(
                            predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };

        return customers.findAll(specification, pageable);
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
            JsonNode customFields,
            long version) {
        Customer value = get(tenantId, id);
        requireVersion(value.getVersion(), version, "Customer");
        if (!Objects.equals(value.getManagerUserId(), managerUserId)) {
            requireActiveManager(tenantId, managerUserId);
        }
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

    private void requireActiveManager(UUID tenantId, UUID managerUserId) {
        if (managerUserId == null) {
            return;
        }
        if (!memberships.existsByTenantIdAndUserIdAndStatus(tenantId, managerUserId, "ACTIVE")) {
            throw new BusinessConflictException(
                    "INVALID_MANAGER", "Manager must be an active tenant member");
        }
    }

    @Transactional
    public Customer changeStatus(UUID tenantId, UUID id, CustomerStatus status, long version) {
        Customer value = get(tenantId, id);
        requireVersion(value.getVersion(), version, "Customer");
        value.changeStatus(status);
        return value;
    }

    @Transactional
    public CustomerEmail addEmail(
            UUID tenantId, UUID customerId, String email, String type, boolean primary) {
        get(tenantId, customerId);
        String normalized = CustomerEmail.normalizeForLookup(email);
        if (emails.existsByTenantIdAndCustomerIdAndEmail(tenantId, customerId, normalized)) {
            throw new BusinessConflictException(
                    "DUPLICATE_CONTACT", "Customer email already exists");
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
            String status,
            long version) {
        get(tenantId, customerId);
        CustomerEmail value =
                emails.findByIdAndTenantIdAndCustomerId(emailId, tenantId, customerId)
                        .orElseThrow(() -> new NoSuchElementException("Customer email not found"));
        requireVersion(value.getVersion(), version, "Customer email");
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
            throw new BusinessConflictException(
                    "DUPLICATE_CONTACT", "Customer phone already exists");
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
            String status,
            long version) {
        get(tenantId, customerId);
        CustomerPhone value =
                phones.findByIdAndTenantIdAndCustomerId(phoneId, tenantId, customerId)
                        .orElseThrow(() -> new NoSuchElementException("Customer phone not found"));
        requireVersion(value.getVersion(), version, "Customer phone");
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

    @Transactional(readOnly = true)
    public List<CustomerPhone> phonesByCustomerIds(UUID tenantId, Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return phones.findAllByTenantIdAndCustomerIdIn(tenantId, customerIds);
    }

    @Transactional(readOnly = true)
    public List<CustomerChannelAddress> channelAddressesByCustomerIds(
            UUID tenantId, Collection<UUID> customerIds, String channel) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return channelAddresses.findAllByTenantIdAndCustomerIdInAndChannel(
                tenantId, customerIds, channel);
    }

    @Transactional
    public CustomerChannelAddress addChannelAddress(
            UUID tenantId,
            UUID customerId,
            String channel,
            String address,
            boolean primary,
            java.time.Instant verifiedAt) {
        get(tenantId, customerId);
        return channelAddresses.save(
                new CustomerChannelAddress(
                        tenantId, customerId, channel, address, primary, verifiedAt));
    }

    @Transactional
    public CustomerSegment createSegment(
            UUID tenantId, String code, String name, String description) {
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        segments.findByTenantIdAndCode(tenantId, normalizedCode)
                .ifPresent(
                        value -> {
                            throw new BusinessConflictException(
                                    "DUPLICATE_SEGMENT_CODE",
                                    "Customer segment code already exists");
                        });
        try {
            return segments.saveAndFlush(
                    new CustomerSegment(tenantId, normalizedCode, name, description));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessConflictException(
                    "DUPLICATE_SEGMENT_CODE", "Customer segment code already exists");
        }
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
            long version) {
        CustomerSegment value = segmentForUpdate(tenantId, segmentId);
        requireVersion(value.getVersion(), version, "Segment");
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
        CustomerSegment segment = segmentForUpdate(tenantId, segmentId);
        if (!segment.isActive()) {
            if (members.existsByTenantIdAndCustomerIdAndSegmentId(
                    tenantId, customerId, segmentId)) {
                return;
            }
            throw new BusinessConflictException(
                    "INACTIVE_SEGMENT", "Inactive segment cannot be assigned");
        }
        members.insertIgnore(UUID.randomUUID(), tenantId, customerId, segmentId);
    }

    @Transactional
    public void removeSegment(UUID tenantId, UUID customerId, UUID segmentId) {
        get(tenantId, customerId);
        segment(tenantId, segmentId);
        members.deleteByTenantIdAndCustomerIdAndSegmentId(tenantId, customerId, segmentId);
    }

    private CustomerSegment segmentForUpdate(UUID tenantId, UUID segmentId) {
        return segments.findForUpdateByIdAndTenantId(segmentId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Segment not found"));
    }

    @Transactional(readOnly = true)
    public List<UUID> segmentIds(UUID tenantId, UUID customerId) {
        get(tenantId, customerId);
        return members.findAllByTenantIdAndCustomerId(tenantId, customerId).stream()
                .map(CustomerSegmentMember::getSegmentId)
                .sorted()
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
        emails
                .findAllByTenantIdAndCustomerIdAndPrimaryTrueAndStatus(
                        tenantId, customerId, "ACTIVE")
                .stream()
                .filter(value -> exceptId == null || !value.getId().equals(exceptId))
                .forEach(CustomerEmail::demotePrimary);
    }

    private void demotePhonePrimaries(UUID tenantId, UUID customerId, UUID exceptId) {
        phones
                .findAllByTenantIdAndCustomerIdAndPrimaryTrueAndStatus(
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

    private static void requireVersion(long actual, long expected, String resource) {
        if (actual != expected) {
            throw new BusinessConflictException("VERSION_CONFLICT", resource + " version conflict");
        }
    }
}
