package io.collectra.api.collection.application;

import io.collectra.api.collection.domain.CollectionCase;
import io.collectra.api.collection.domain.CollectionCaseStatus;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.collection.infrastructure.CollectionCaseRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.shared.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
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
public class CollectionQueryService {
    public static final int MAX_SIZE = 200;
    private static final Set<String> SORTS =
            Set.of("createdAt", "updatedAt", "openedAt", "priority", "status");

    private final CollectionCaseRepository cases;
    private final ReceivableService receivables;

    public CollectionQueryService(CollectionCaseRepository cases, ReceivableService receivables) {
        this.cases = cases;
        this.receivables = receivables;
    }

    @Transactional(readOnly = true)
    public CasePage list(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo,
            int page,
            int size,
            String sort) {
        if (page < 0 || size <= 0 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
        Page<CollectionCase> result =
                cases.findAll(
                        specification(tenantId, customerId, invoiceId, status, priority, assignedTo),
                        PageRequest.of(page, size, parseSort(sort)));
        List<CaseItem> items =
                result.getContent().stream().map(value -> item(tenantId, value)).toList();
        return new CasePage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private CaseItem item(UUID tenantId, CollectionCase value) {
        Invoice invoice = receivables.invoice(tenantId, value.getInvoiceId());
        return new CaseItem(
                value.getId(),
                value.getCustomerId(),
                value.getInvoiceId(),
                value.getStatus(),
                value.getPriority(),
                value.getAssignedTo(),
                invoice.getCurrency(),
                invoice.getOutstandingAmount(),
                invoice.getPaymentStatus().name(),
                value.getOpenedAt(),
                value.getClosedAt(),
                value.getCloseReason(),
                value.getVersion());
    }

    private static Specification<CollectionCase> specification(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (customerId != null) predicates.add(cb.equal(root.get("customerId"), customerId));
            if (invoiceId != null) predicates.add(cb.equal(root.get("invoiceId"), invoiceId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (assignedTo != null) predicates.add(cb.equal(root.get("assignedTo"), assignedTo));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort parseSort(String value) {
        String normalized = value == null || value.isBlank() ? "createdAt,desc" : value.trim();
        String[] parts = normalized.split(",", -1);
        if (parts.length != 2 || !SORTS.contains(parts[0].trim())) {
            throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort field");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort direction");
        }
        return Sort.by(direction, parts[0].trim()).and(Sort.by(direction, "id"));
    }

    public record CaseItem(
            UUID id,
            UUID customerId,
            UUID invoiceId,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo,
            String currency,
            BigDecimal outstandingAmount,
            String paymentStatus,
            java.time.Instant openedAt,
            java.time.Instant closedAt,
            io.collectra.api.collection.domain.CollectionCloseReason closeReason,
            long version) {}

    public record CasePage(
            List<CaseItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
