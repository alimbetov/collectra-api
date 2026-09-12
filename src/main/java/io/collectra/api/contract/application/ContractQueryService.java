package io.collectra.api.contract.application;

import io.collectra.api.contract.domain.Contract;
import io.collectra.api.contract.domain.ContractStatus;
import io.collectra.api.contract.infrastructure.ContractRepository;
import io.collectra.api.shared.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractQueryService {
    public static final int MAX_SIZE = 200;

    private static final Set<String> SORTS =
            Set.of(
                    "createdAt",
                    "updatedAt",
                    "contractNumber",
                    "externalId",
                    "status",
                    "validFrom",
                    "validTo",
                    "renewalDate");

    private final ContractRepository contracts;

    public ContractQueryService(ContractRepository contracts) {
        this.contracts = contracts;
    }

    @Transactional(readOnly = true)
    public ContractPage list(
            UUID tenantId,
            String search,
            UUID customerId,
            ContractStatus status,
            String externalId,
            LocalDate validFrom,
            LocalDate validTo,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size,
            String sort) {
        validatePage(page, size);
        validateRange(validFrom, validTo, "validFrom", "validTo");
        validateRange(createdFrom, createdTo, "createdFrom", "createdTo");

        Page<Contract> result =
                contracts.findAll(
                        specification(
                                tenantId,
                                normalizeSearch(search),
                                customerId,
                                status,
                                trimToNull(externalId),
                                validFrom,
                                validTo,
                                createdFrom,
                                createdTo),
                        PageRequest.of(page, size, parseSort(sort)));

        return new ContractPage(
                result.getContent().stream().map(ContractItem::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private static Specification<Contract> specification(
            UUID tenantId,
            String search,
            UUID customerId,
            ContractStatus status,
            String externalId,
            LocalDate validFrom,
            LocalDate validTo,
            Instant createdFrom,
            Instant createdTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (externalId != null) {
                predicates.add(cb.equal(root.get("externalId"), externalId));
            }
            if (validFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("validFrom"), validFrom));
            }
            if (validTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("validTo"), validTo));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            if (search != null) {
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.<String>get("contractNumber")), search + "%"),
                                cb.like(cb.lower(root.<String>get("externalId")), search + "%")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort parseSort(String value) {
        String normalized = trimToNull(value);
        String field = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;
        if (normalized != null) {
            String[] parts = normalized.split(",", -1);
            if (parts.length != 2 || !SORTS.contains(parts[0].trim())) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort field");
            }
            field = parts[0].trim();
            try {
                direction = Sort.Direction.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort direction");
            }
        }
        return Sort.by(direction, field).and(Sort.by(direction, "id"));
    }

    private static String normalizeSearch(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T extends Comparable<? super T>> void validateRange(
            T from, T to, String fromName, String toName) {
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new InvalidRequestException(
                    "INVALID_RANGE", fromName + " must not be after " + toName);
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size <= 0 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
    }

    public record ContractItem(
            UUID id,
            UUID customerId,
            String externalId,
            String contractNumber,
            ContractStatus status,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static ContractItem from(Contract value) {
            return new ContractItem(
                    value.getId(),
                    value.getCustomerId(),
                    value.getExternalId(),
                    value.getContractNumber(),
                    value.getStatus(),
                    value.getValidFrom(),
                    value.getValidTo(),
                    value.getRenewalDate(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }

    public record ContractPage(
            List<ContractItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
