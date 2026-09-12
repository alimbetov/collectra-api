package io.collectra.api.customer.application;

import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import io.collectra.api.customer.domain.CustomerSegment;
import io.collectra.api.customer.domain.CustomerSegmentMember;
import io.collectra.api.customer.domain.CustomerStatus;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.customer.infrastructure.CustomerRepository;
import io.collectra.api.customer.infrastructure.CustomerSegmentMemberRepository;
import io.collectra.api.customer.infrastructure.CustomerSegmentRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerQueryService {
    public static final int MAX_SIZE = 200;

    private static final Set<String> CUSTOMER_SORTS =
            Set.of("createdAt", "updatedAt", "displayName", "externalId");
    private static final Set<String> SEGMENT_SORTS =
            Set.of("createdAt", "updatedAt", "name", "code");

    private final CustomerRepository customers;
    private final CustomerSegmentRepository segments;
    private final CustomerSegmentMemberRepository members;

    public CustomerQueryService(
            CustomerRepository customers,
            CustomerSegmentRepository segments,
            CustomerSegmentMemberRepository members) {
        this.customers = customers;
        this.segments = segments;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public CustomerPage customers(
            UUID tenantId,
            String search,
            CustomerStatus status,
            CustomerType customerType,
            UUID managerId,
            UUID segmentId,
            String externalId,
            String email,
            String phone,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        validateRange(createdFrom, createdTo);

        Page<Customer> result =
                customers.search(
                        tenantId,
                        normalizeSearch(search),
                        status,
                        customerType,
                        managerId,
                        segmentId,
                        trimToNull(externalId),
                        normalizeEmail(email),
                        normalizePhone(phone),
                        createdFrom,
                        createdTo,
                        PageRequest.of(
                                page,
                                size,
                                parseSort(sort, CUSTOMER_SORTS, "createdAt", Sort.Direction.DESC)));

        List<UUID> customerIds = result.getContent().stream().map(Customer::getId).toList();
        Map<UUID, List<UUID>> segmentIdsByCustomer = new LinkedHashMap<>();
        if (!customerIds.isEmpty()) {
            for (CustomerSegmentMember membership :
                    members.findAllByTenantIdAndCustomerIdIn(tenantId, customerIds)) {
                segmentIdsByCustomer
                        .computeIfAbsent(membership.getCustomerId(), ignored -> new ArrayList<>())
                        .add(membership.getSegmentId());
            }
        }
        segmentIdsByCustomer.values().forEach(values -> values.sort(UUID::compareTo));

        List<CustomerListItem> items =
                result.getContent().stream()
                        .map(
                                value ->
                                        new CustomerListItem(
                                                value.getId(),
                                                value.getExternalId(),
                                                value.getCustomerType(),
                                                value.getDisplayName(),
                                                value.getStatus(),
                                                value.getManagerUserId(),
                                                value.getPreferredLocale(),
                                                value.getTimezone(),
                                                List.copyOf(
                                                        segmentIdsByCustomer.getOrDefault(
                                                                value.getId(), List.of())),
                                                value.getCreatedAt(),
                                                value.getUpdatedAt()))
                        .toList();
        return new CustomerPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    @Transactional(readOnly = true)
    public SegmentPage segments(
            UUID tenantId, String search, Boolean active, int page, int size, String sort) {
        validatePage(page, size);
        Page<CustomerSegment> result =
                segments.search(
                        tenantId,
                        normalizeSearch(search),
                        active,
                        PageRequest.of(
                                page,
                                size,
                                parseSort(sort, SEGMENT_SORTS, "name", Sort.Direction.ASC)));
        List<SegmentItem> items = result.getContent().stream().map(SegmentItem::from).toList();
        return new SegmentPage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
    }

    private static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "INVALID_RANGE", "createdFrom must not be after createdTo");
        }
    }

    private static Sort parseSort(
            String value,
            Set<String> allowed,
            String defaultField,
            Sort.Direction defaultDirection) {
        String normalized = trimToNull(value);
        String field = defaultField;
        Sort.Direction direction = defaultDirection;
        if (normalized != null) {
            String[] parts = normalized.split(",", -1);
            if (parts.length != 2 || !allowed.contains(parts[0].trim())) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort field");
            }
            field = parts[0].trim();
            String rawDirection = parts[1].trim().toUpperCase(Locale.ROOT);
            try {
                direction = Sort.Direction.valueOf(rawDirection);
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort direction");
            }
        }
        Sort primary = Sort.by(direction, field);
        return "id".equals(field) ? primary : primary.and(Sort.by(direction, "id"));
    }

    private static String normalizeSearch(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return CustomerEmail.normalizeForLookup(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid email filter");
        }
    }

    private static String normalizePhone(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return CustomerPhone.normalizeForLookup(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid phone filter");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CustomerListItem(
            UUID id,
            String externalId,
            CustomerType customerType,
            String displayName,
            CustomerStatus status,
            UUID managerUserId,
            String preferredLocale,
            String timezone,
            List<UUID> segmentIds,
            Instant createdAt,
            Instant updatedAt) {}

    public record CustomerPage(
            List<CustomerListItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}

    public record SegmentItem(
            UUID id,
            String code,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static SegmentItem from(CustomerSegment value) {
            return new SegmentItem(
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

    public record SegmentPage(
            List<SegmentItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
