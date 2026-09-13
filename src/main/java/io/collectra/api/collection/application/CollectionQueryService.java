package io.collectra.api.collection.application;

import io.collectra.api.collection.domain.CollectionAction;
import io.collectra.api.collection.domain.CollectionActionStatus;
import io.collectra.api.collection.domain.CollectionCase;
import io.collectra.api.collection.domain.CollectionCaseStatus;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.collection.infrastructure.CollectionActionRepository;
import io.collectra.api.collection.infrastructure.CollectionCaseRepository;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.identity.application.IdentityDirectoryService;
import io.collectra.api.identity.application.IdentityDirectoryService.UserSummary;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.shared.error.InvalidRequestException;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final CollectionActionRepository actions;
    private final ReceivableService receivables;
    private final CustomerService customers;
    private final IdentityDirectoryService identityDirectory;
    private final Clock clock;

    public CollectionQueryService(
            CollectionCaseRepository cases,
            CollectionActionRepository actions,
            ReceivableService receivables,
            CustomerService customers,
            IdentityDirectoryService identityDirectory,
            Clock clock) {
        this.cases = cases;
        this.actions = actions;
        this.receivables = receivables;
        this.customers = customers;
        this.identityDirectory = identityDirectory;
        this.clock = clock;
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

        List<CollectionCase> values = result.getContent();
        Set<UUID> customerIds = values.stream().map(CollectionCase::getCustomerId).collect(Collectors.toSet());
        Set<UUID> invoiceIds = values.stream().map(CollectionCase::getInvoiceId).collect(Collectors.toSet());
        Set<UUID> assigneeIds = values.stream()
                .map(CollectionCase::getAssignedTo)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<UUID> caseIds = values.stream().map(CollectionCase::getId).toList();

        Map<UUID, Customer> customersById = customers.customersByIds(tenantId, customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, Invoice> invoicesById = receivables.invoicesByIds(tenantId, invoiceIds).stream()
                .collect(Collectors.toMap(Invoice::getId, Function.identity()));
        Map<UUID, UserSummary> assigneesById = identityDirectory.users(tenantId, assigneeIds).stream()
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));

        Map<UUID, CollectionAction> nextActionByCase = new java.util.LinkedHashMap<>();
        if (!caseIds.isEmpty()) {
            actions.findAllByTenantIdAndCaseIdInAndStatusOrderByDueAtAsc(
                            tenantId, caseIds, CollectionActionStatus.PENDING)
                    .forEach(action -> nextActionByCase.putIfAbsent(action.getCaseId(), action));
        }

        Instant asOf = clock.instant();
        List<CaseItem> items = values.stream()
                .map(value -> item(value, customersById, invoicesById, assigneesById, nextActionByCase, asOf))
                .toList();
        return new CasePage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private CaseItem item(
            CollectionCase value,
            Map<UUID, Customer> customersById,
            Map<UUID, Invoice> invoicesById,
            Map<UUID, UserSummary> assigneesById,
            Map<UUID, CollectionAction> nextActionByCase,
            Instant asOf) {
        Invoice invoice = invoicesById.get(value.getInvoiceId());
        Customer customer = customersById.get(value.getCustomerId());
        UserSummary assignee = assigneesById.get(value.getAssignedTo());
        CollectionAction nextAction = nextActionByCase.get(value.getId());

        return new CaseItem(
                value.getId(),
                value.getCustomerId(),
                customer == null ? null : customer.getDisplayName(),
                value.getInvoiceId(),
                invoice == null ? null : invoice.getInvoiceNumber(),
                value.getStatus(),
                value.getPriority(),
                value.getAssignedTo(),
                assignee == null ? null : assignee.label(),
                invoice == null ? null : invoice.getCurrency(),
                invoice == null ? null : invoice.getOutstandingAmount(),
                invoice == null ? null : invoice.getPaymentStatus().name(),
                nextAction == null ? null : nextAction.getActionType(),
                nextAction == null ? null : nextAction.getDueAt(),
                nextAction != null && nextAction.isOverdue(asOf),
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
            String customerDisplayName,
            UUID invoiceId,
            String invoiceNumber,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo,
            String assigneeDisplayName,
            String currency,
            BigDecimal outstandingAmount,
            String paymentStatus,
            String nextActionType,
            Instant nextActionDueAt,
            boolean nextActionOverdue,
            Instant openedAt,
            Instant closedAt,
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
