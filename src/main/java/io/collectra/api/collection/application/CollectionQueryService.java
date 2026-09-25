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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionQueryService {
    public static final int MAX_SIZE = 200;
    private static final Set<String> SORTS =
            Set.of("createdAt", "updatedAt", "openedAt", "priority", "status", "nextActionDueAt");

    private final CollectionCaseRepository cases;
    private final CollectionActionRepository actions;
    private final ReceivableService receivables;
    private final CustomerService customers;
    private final IdentityDirectoryService identityDirectory;
    private final Clock clock;
    private final NamedParameterJdbcTemplate jdbc;

    public CollectionQueryService(
            CollectionCaseRepository cases,
            CollectionActionRepository actions,
            ReceivableService receivables,
            CustomerService customers,
            IdentityDirectoryService identityDirectory,
            Clock clock,
            NamedParameterJdbcTemplate jdbc) {
        this.cases = cases;
        this.actions = actions;
        this.receivables = receivables;
        this.customers = customers;
        this.identityDirectory = identityDirectory;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CasePage list(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo,
            Boolean nextActionOverdue,
            Instant nextActionDueFrom,
            Instant nextActionDueTo,
            int page,
            int size,
            String sort) {
        if (page < 0 || size <= 0 || size > MAX_SIZE) {
            throw new InvalidRequestException("INVALID_REQUEST", "Invalid pagination parameters");
        }
        if (nextActionDueFrom != null
                && nextActionDueTo != null
                && nextActionDueFrom.isAfter(nextActionDueTo)) {
            throw new InvalidRequestException(
                    "INVALID_REQUEST", "nextActionDueFrom must be before nextActionDueTo");
        }
        boolean nextActionFilter =
                nextActionOverdue != null || nextActionDueFrom != null || nextActionDueTo != null;
        boolean nextActionSort = sort != null && sort.trim().startsWith("nextActionDueAt,");
        if (nextActionFilter || nextActionSort) {
            return listWithNextActionCriteria(
                    tenantId,
                    customerId,
                    invoiceId,
                    status,
                    priority,
                    assignedTo,
                    nextActionOverdue,
                    nextActionDueFrom,
                    nextActionDueTo,
                    page,
                    size,
                    sort);
        }
        Page<CollectionCase> result =
                cases.findAll(
                        specification(
                                tenantId, customerId, invoiceId, status, priority, assignedTo),
                        PageRequest.of(page, size, parseSort(sort)));

        List<CollectionCase> values = result.getContent();
        Set<UUID> customerIds =
                values.stream().map(CollectionCase::getCustomerId).collect(Collectors.toSet());
        Set<UUID> invoiceIds =
                values.stream().map(CollectionCase::getInvoiceId).collect(Collectors.toSet());
        Set<UUID> assigneeIds =
                values.stream()
                        .map(CollectionCase::getAssignedTo)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        List<UUID> caseIds = values.stream().map(CollectionCase::getId).toList();

        Map<UUID, Customer> customersById =
                customers.customersByIds(tenantId, customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, Invoice> invoicesById =
                receivables.invoicesByIds(tenantId, invoiceIds).stream()
                        .collect(Collectors.toMap(Invoice::getId, Function.identity()));
        Map<UUID, UserSummary> assigneesById =
                identityDirectory.users(tenantId, assigneeIds).stream()
                        .collect(Collectors.toMap(UserSummary::id, Function.identity()));

        Map<UUID, CollectionAction> nextActionByCase = new java.util.LinkedHashMap<>();
        if (!caseIds.isEmpty()) {
            actions.findAllByTenantIdAndCaseIdInAndStatusOrderByDueAtAsc(
                            tenantId, caseIds, CollectionActionStatus.PENDING)
                    .forEach(action -> nextActionByCase.putIfAbsent(action.getCaseId(), action));
        }

        Instant asOf = clock.instant();
        List<CaseItem> items =
                values.stream()
                        .map(
                                value ->
                                        item(
                                                value,
                                                customersById,
                                                invoicesById,
                                                assigneesById,
                                                nextActionByCase,
                                                asOf))
                        .toList();
        return new CasePage(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext());
    }

    private CasePage listWithNextActionCriteria(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo,
            Boolean nextActionOverdue,
            Instant nextActionDueFrom,
            Instant nextActionDueTo,
            int page,
            int size,
            String sort) {
        Instant asOf = clock.instant();
        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("customerId", customerId)
                        .addValue("invoiceId", invoiceId)
                        .addValue("status", status == null ? null : status.name())
                        .addValue("priority", priority == null ? null : priority.name())
                        .addValue("assignedTo", assignedTo)
                        .addValue("asOf", asOf)
                        .addValue("dueFrom", nextActionDueFrom)
                        .addValue("dueTo", nextActionDueTo)
                        .addValue("limit", size)
                        .addValue("offset", (long) page * size);

        StringBuilder where = new StringBuilder("WHERE c.tenant_id = :tenantId\n");
        if (customerId != null) {
            where.append(" AND c.customer_id = :customerId\n");
        }
        if (invoiceId != null) {
            where.append(" AND c.invoice_id = :invoiceId\n");
        }
        if (status != null) {
            where.append(" AND c.status = :status\n");
        }
        if (priority != null) {
            where.append(" AND c.priority = :priority\n");
        }
        if (assignedTo != null) {
            where.append(" AND c.assigned_to = :assignedTo\n");
        }
        if (nextActionDueFrom != null) {
            where.append(" AND next_action.due_at >= :dueFrom\n");
        }
        if (nextActionDueTo != null) {
            where.append(" AND next_action.due_at <= :dueTo\n");
        }
        if (nextActionOverdue != null) {
            where.append(
                    nextActionOverdue
                            ? " AND next_action.due_at < :asOf\n"
                            : " AND (next_action.due_at IS NULL OR next_action.due_at >= :asOf)\n");
        }

        String from =
                """
                FROM collection_cases c
                LEFT JOIN LATERAL (
                    SELECT a.action_type, a.due_at
                    FROM collection_actions a
                    WHERE a.tenant_id = c.tenant_id
                      AND a.case_id = c.id
                      AND a.status = 'PENDING'
                    ORDER BY a.due_at ASC, a.id ASC
                    LIMIT 1
                ) next_action ON TRUE
                """;
        SortSpec sortSpec = parseOperationalSort(sort);
        List<QueueRow> rows =
                jdbc.query(
                        """
                        SELECT c.id, next_action.action_type, next_action.due_at
                        """
                                + from
                                + where
                                + " ORDER BY "
                                + sortSpec.sql()
                                + " LIMIT :limit OFFSET :offset",
                        parameters,
                        (rs, rowNum) ->
                                new QueueRow(
                                        rs.getObject("id", UUID.class),
                                        rs.getString("action_type"),
                                        rs.getTimestamp("due_at") == null
                                                ? null
                                                : rs.getTimestamp("due_at").toInstant()));
        long total = jdbc.queryForObject(
                "SELECT count(*) " + from + where, parameters, Long.class);
        List<UUID> ids = rows.stream().map(QueueRow::id).toList();
        Map<UUID, CollectionCase> casesById =
                cases.findAllById(ids).stream()
                        .filter(value -> value.getTenantId().equals(tenantId))
                        .collect(Collectors.toMap(CollectionCase::getId, Function.identity()));
        Set<UUID> customerIds =
                casesById.values().stream()
                        .map(CollectionCase::getCustomerId)
                        .collect(Collectors.toSet());
        Set<UUID> invoiceIds =
                casesById.values().stream()
                        .map(CollectionCase::getInvoiceId)
                        .collect(Collectors.toSet());
        Set<UUID> assigneeIds =
                casesById.values().stream()
                        .map(CollectionCase::getAssignedTo)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<UUID, Customer> customersById =
                customers.customersByIds(tenantId, customerIds).stream()
                        .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, Invoice> invoicesById =
                receivables.invoicesByIds(tenantId, invoiceIds).stream()
                        .collect(Collectors.toMap(Invoice::getId, Function.identity()));
        Map<UUID, UserSummary> assigneesById =
                identityDirectory.users(tenantId, assigneeIds).stream()
                        .collect(Collectors.toMap(UserSummary::id, Function.identity()));
        List<CaseItem> items =
                rows.stream()
                        .map(
                                row -> {
                                    CollectionCase value = casesById.get(row.id());
                                    return item(
                                            value,
                                            customersById,
                                            invoicesById,
                                            assigneesById,
                                            row.actionType(),
                                            row.dueAt(),
                                            asOf);
                                })
                        .toList();
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new CasePage(items, page, size, total, totalPages, page + 1 < totalPages);
    }

    private static SortSpec parseOperationalSort(String value) {
        String normalized = value == null || value.isBlank() ? "createdAt,desc" : value.trim();
        String[] parts = normalized.split(",", -1);
        if (parts.length != 2 || !SORTS.contains(parts[0].trim())) {
            throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort field");
        }
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new InvalidRequestException("INVALID_REQUEST", "Unsupported sort direction");
        }
        String field =
                switch (parts[0].trim()) {
                    case "nextActionDueAt" -> "next_action.due_at";
                    case "createdAt" -> "c.created_at";
                    case "updatedAt" -> "c.updated_at";
                    case "openedAt" -> "c.opened_at";
                    case "priority" -> "c.priority";
                    case "status" -> "c.status";
                    default ->
                            throw new InvalidRequestException(
                                    "INVALID_REQUEST", "Unsupported sort field");
                };
        String nulls = parts[0].trim().equals("nextActionDueAt") ? " NULLS LAST" : "";
        return new SortSpec(field + " " + direction + nulls + ", c.id " + direction);
    }

    private record QueueRow(UUID id, String actionType, Instant dueAt) {}

    private record SortSpec(String sql) {}

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

        return item(
                value,
                customersById,
                invoicesById,
                assigneesById,
                nextAction == null ? null : nextAction.getActionType(),
                nextAction == null ? null : nextAction.getDueAt(),
                asOf);
    }

    private CaseItem item(
            CollectionCase value,
            Map<UUID, Customer> customersById,
            Map<UUID, Invoice> invoicesById,
            Map<UUID, UserSummary> assigneesById,
            String nextActionType,
            Instant nextActionDueAt,
            Instant asOf) {
        Invoice invoice = invoicesById.get(value.getInvoiceId());
        Customer customer = customersById.get(value.getCustomerId());
        UserSummary assignee = assigneesById.get(value.getAssignedTo());

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
                nextActionType,
                nextActionDueAt,
                nextActionDueAt != null && nextActionDueAt.isBefore(asOf),
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
