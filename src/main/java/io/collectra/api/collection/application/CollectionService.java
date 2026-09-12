package io.collectra.api.collection.application;

import io.collectra.api.collection.domain.CollectionAction;
import io.collectra.api.collection.domain.CollectionCase;
import io.collectra.api.collection.domain.CollectionCaseStatus;
import io.collectra.api.collection.domain.CollectionCloseReason;
import io.collectra.api.collection.domain.CollectionEvent;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.collection.domain.Dispute;
import io.collectra.api.collection.domain.PromiseToPay;
import io.collectra.api.collection.infrastructure.CollectionActionRepository;
import io.collectra.api.collection.infrastructure.CollectionCaseRepository;
import io.collectra.api.collection.infrastructure.CollectionEventRepository;
import io.collectra.api.collection.infrastructure.DisputeRepository;
import io.collectra.api.collection.infrastructure.PromiseToPayRepository;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.PaymentStatus;
import io.collectra.api.shared.error.BusinessConflictException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollectionService {
    private static final EnumSet<CollectionCaseStatus> ACTIVE_CASE_STATUSES =
            EnumSet.of(
                    CollectionCaseStatus.OPEN,
                    CollectionCaseStatus.IN_PROGRESS,
                    CollectionCaseStatus.ON_HOLD);

    private final CollectionCaseRepository cases;
    private final PromiseToPayRepository promises;
    private final DisputeRepository disputes;
    private final CollectionActionRepository actions;
    private final CollectionEventRepository events;
    private final ReceivableService receivables;
    private final Clock clock;

    public CollectionService(
            CollectionCaseRepository cases,
            PromiseToPayRepository promises,
            DisputeRepository disputes,
            CollectionActionRepository actions,
            CollectionEventRepository events,
            ReceivableService receivables,
            Clock clock) {
        this.cases = cases;
        this.promises = promises;
        this.disputes = disputes;
        this.actions = actions;
        this.events = events;
        this.receivables = receivables;
        this.clock = clock;
    }

    @Transactional
    public CollectionCase createCase(
            UUID tenantId,
            UUID customerId,
            UUID invoiceId,
            CollectionPriority priority,
            UUID assignedTo,
            String actor) {
        Invoice invoice = receivables.invoice(tenantId, invoiceId);
        if (!invoice.getCustomerId().equals(customerId)) {
            throw new BusinessConflictException(
                    "CUSTOMER_MISMATCH", "Invoice belongs to another customer");
        }
        if (invoice.getOutstandingAmount().signum() <= 0
                || invoice.getPaymentStatus() == PaymentStatus.PAID
                || invoice.getPaymentStatus() == PaymentStatus.CANCELLED) {
            throw new BusinessConflictException(
                    "INVALID_STATE_TRANSITION", "Settled invoice cannot enter collection");
        }
        if (cases.existsByTenantIdAndInvoiceIdAndStatusIn(
                tenantId, invoiceId, ACTIVE_CASE_STATUSES)) {
            throw new BusinessConflictException(
                    "COLLECTION_CASE_ALREADY_ACTIVE",
                    "An active collection case already exists for the invoice");
        }
        Instant now = Instant.now(clock);
        CollectionCase value =
                cases.save(
                        new CollectionCase(
                                tenantId, customerId, invoiceId, priority, assignedTo, now));
        event(value, "CASE_OPENED", "COLLECTION_CASE", value.getId(), actor, null, now);
        return value;
    }

    @Transactional(readOnly = true)
    public CollectionCase getCase(UUID tenantId, UUID caseId) {
        return cases.findByIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Collection case not found"));
    }

    @Transactional
    public CollectionCase updateCase(
            UUID tenantId,
            UUID caseId,
            long version,
            CollectionPriority priority,
            UUID assignedTo,
            String actor) {
        CollectionCase value = getCase(tenantId, caseId);
        requireVersion(value.getVersion(), version, "Collection case");
        try {
            value.update(priority, assignedTo);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(value, "CASE_UPDATED", "COLLECTION_CASE", value.getId(), actor, null, Instant.now(clock));
        return value;
    }

    @Transactional
    public CollectionCase start(UUID tenantId, UUID caseId, long version, String actor) {
        return transitionCase(tenantId, caseId, version, actor, "CASE_STARTED", CollectionCase::start);
    }

    @Transactional
    public CollectionCase hold(UUID tenantId, UUID caseId, long version, String actor) {
        return transitionCase(tenantId, caseId, version, actor, "CASE_HELD", CollectionCase::hold);
    }

    @Transactional
    public CollectionCase close(
            UUID tenantId,
            UUID caseId,
            long version,
            CollectionCloseReason reason,
            String actor) {
        CollectionCase value = getCase(tenantId, caseId);
        requireVersion(value.getVersion(), version, "Collection case");
        Instant now = Instant.now(clock);
        try {
            value.close(reason, now);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(value, "CASE_CLOSED", "COLLECTION_CASE", value.getId(), actor, reason.name(), now);
        return value;
    }

    @Transactional
    public PromiseToPay createPromise(
            UUID tenantId,
            UUID caseId,
            BigDecimal amount,
            String currency,
            java.time.LocalDate promisedDate,
            String actor) {
        CollectionCase collectionCase = requireActiveCase(tenantId, caseId);
        Invoice invoice = receivables.invoice(tenantId, collectionCase.getInvoiceId());
        if (!invoice.getCurrency().equalsIgnoreCase(currency)) {
            throw new BusinessConflictException(
                    "CURRENCY_MISMATCH", "Promise currency must match invoice currency");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessConflictException("INVALID_REQUEST", "Promise amount must be positive");
        }
        if (amount.compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new BusinessConflictException(
                    "ALLOCATION_EXCEEDS_INVOICE",
                    "Promise amount exceeds invoice outstanding amount");
        }
        PromiseToPay value =
                promises.save(new PromiseToPay(tenantId, caseId, amount, currency, promisedDate));
        event(
                collectionCase,
                "PROMISE_CREATED",
                "PROMISE_TO_PAY",
                value.getId(),
                actor,
                null,
                Instant.now(clock));
        return value;
    }

    @Transactional(readOnly = true)
    public List<PromiseToPay> promises(UUID tenantId, UUID caseId) {
        getCase(tenantId, caseId);
        return promises.findAllByTenantIdAndCaseIdOrderByCreatedAtDesc(tenantId, caseId);
    }

    @Transactional
    public PromiseToPay fulfillPromise(
            UUID tenantId, UUID caseId, UUID promiseId, long version, String actor) {
        return transitionPromise(tenantId, caseId, promiseId, version, actor, "PROMISE_FULFILLED", PromiseToPay::fulfill);
    }

    @Transactional
    public PromiseToPay breakPromise(
            UUID tenantId, UUID caseId, UUID promiseId, long version, String actor) {
        return transitionPromise(tenantId, caseId, promiseId, version, actor, "PROMISE_BROKEN", PromiseToPay::breakPromise);
    }

    @Transactional
    public PromiseToPay cancelPromise(
            UUID tenantId, UUID caseId, UUID promiseId, long version, String actor) {
        return transitionPromise(tenantId, caseId, promiseId, version, actor, "PROMISE_CANCELLED", PromiseToPay::cancel);
    }

    @Transactional
    public Dispute createDispute(
            UUID tenantId, UUID caseId, String reason, String description, String actor) {
        CollectionCase collectionCase = requireActiveCase(tenantId, caseId);
        Dispute value = disputes.save(new Dispute(tenantId, caseId, reason, description));
        event(collectionCase, "DISPUTE_OPENED", "DISPUTE", value.getId(), actor, reason, Instant.now(clock));
        return value;
    }

    @Transactional(readOnly = true)
    public List<Dispute> disputes(UUID tenantId, UUID caseId) {
        getCase(tenantId, caseId);
        return disputes.findAllByTenantIdAndCaseIdOrderByCreatedAtDesc(tenantId, caseId);
    }

    @Transactional
    public Dispute resolveDispute(
            UUID tenantId,
            UUID caseId,
            UUID disputeId,
            long version,
            String resolutionCode,
            String summary,
            String actor) {
        CollectionCase collectionCase = getCase(tenantId, caseId);
        Dispute value = dispute(tenantId, caseId, disputeId);
        requireVersion(value.getVersion(), version, "Dispute");
        Instant now = Instant.now(clock);
        try {
            value.resolve(resolutionCode, summary, actor, now);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(collectionCase, "DISPUTE_RESOLVED", "DISPUTE", value.getId(), actor, resolutionCode, now);
        return value;
    }

    @Transactional
    public Dispute cancelDispute(
            UUID tenantId, UUID caseId, UUID disputeId, long version, String actor) {
        CollectionCase collectionCase = getCase(tenantId, caseId);
        Dispute value = dispute(tenantId, caseId, disputeId);
        requireVersion(value.getVersion(), version, "Dispute");
        Instant now = Instant.now(clock);
        try {
            value.cancel(actor, now);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(collectionCase, "DISPUTE_CANCELLED", "DISPUTE", value.getId(), actor, null, now);
        return value;
    }

    @Transactional
    public CollectionAction createAction(
            UUID tenantId,
            UUID caseId,
            String actionType,
            String description,
            Instant dueAt,
            CollectionPriority priority,
            String actor) {
        CollectionCase collectionCase = requireActiveCase(tenantId, caseId);
        CollectionAction value =
                actions.save(
                        new CollectionAction(
                                tenantId, caseId, actionType, description, dueAt, priority));
        event(collectionCase, "ACTION_CREATED", "COLLECTION_ACTION", value.getId(), actor, actionType, Instant.now(clock));
        return value;
    }

    @Transactional(readOnly = true)
    public List<CollectionAction> actions(UUID tenantId, UUID caseId) {
        getCase(tenantId, caseId);
        return actions.findAllByTenantIdAndCaseIdOrderByDueAtAsc(tenantId, caseId);
    }

    @Transactional
    public CollectionAction completeAction(
            UUID tenantId, UUID caseId, UUID actionId, long version, String actor) {
        return transitionAction(tenantId, caseId, actionId, version, actor, "ACTION_COMPLETED", CollectionAction::complete);
    }

    @Transactional
    public CollectionAction cancelAction(
            UUID tenantId, UUID caseId, UUID actionId, long version, String actor) {
        return transitionAction(tenantId, caseId, actionId, version, actor, "ACTION_CANCELLED", CollectionAction::cancel);
    }

    @Transactional(readOnly = true)
    public List<CollectionEvent> timeline(UUID tenantId, UUID caseId) {
        getCase(tenantId, caseId);
        return events.findAllByTenantIdAndCaseIdOrderByEventAtDescIdDesc(tenantId, caseId);
    }

    private CollectionCase transitionCase(
            UUID tenantId,
            UUID caseId,
            long version,
            String actor,
            String eventType,
            java.util.function.Consumer<CollectionCase> command) {
        CollectionCase value = getCase(tenantId, caseId);
        requireVersion(value.getVersion(), version, "Collection case");
        try {
            command.accept(value);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(value, eventType, "COLLECTION_CASE", value.getId(), actor, null, Instant.now(clock));
        return value;
    }

    private PromiseToPay transitionPromise(
            UUID tenantId,
            UUID caseId,
            UUID promiseId,
            long version,
            String actor,
            String eventType,
            java.util.function.Consumer<Instant> ignored) {
        throw new UnsupportedOperationException();
    }

    private PromiseToPay transitionPromise(
            UUID tenantId,
            UUID caseId,
            UUID promiseId,
            long version,
            String actor,
            String eventType,
            java.util.function.BiConsumer<PromiseToPay, Instant> command) {
        CollectionCase collectionCase = getCase(tenantId, caseId);
        PromiseToPay value =
                promises.findByIdAndTenantIdAndCaseId(promiseId, tenantId, caseId)
                        .orElseThrow(() -> new NoSuchElementException("Promise to pay not found"));
        requireVersion(value.getVersion(), version, "Promise to pay");
        Instant now = Instant.now(clock);
        try {
            command.accept(value, now);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(collectionCase, eventType, "PROMISE_TO_PAY", value.getId(), actor, null, now);
        return value;
    }

    private CollectionAction transitionAction(
            UUID tenantId,
            UUID caseId,
            UUID actionId,
            long version,
            String actor,
            String eventType,
            java.util.function.BiConsumer<CollectionAction, Instant> command) {
        CollectionCase collectionCase = getCase(tenantId, caseId);
        CollectionAction value =
                actions.findByIdAndTenantIdAndCaseId(actionId, tenantId, caseId)
                        .orElseThrow(() -> new NoSuchElementException("Collection action not found"));
        requireVersion(value.getVersion(), version, "Collection action");
        Instant now = Instant.now(clock);
        try {
            command.accept(value, now);
        } catch (IllegalStateException ex) {
            throw invalidTransition(ex);
        }
        event(collectionCase, eventType, "COLLECTION_ACTION", value.getId(), actor, null, now);
        return value;
    }

    private CollectionCase requireActiveCase(UUID tenantId, UUID caseId) {
        CollectionCase value = getCase(tenantId, caseId);
        if (value.getStatus() == CollectionCaseStatus.CLOSED) {
            throw new BusinessConflictException(
                    "INVALID_STATE_TRANSITION", "Closed collection case cannot be modified");
        }
        return value;
    }

    private Dispute dispute(UUID tenantId, UUID caseId, UUID disputeId) {
        return disputes.findByIdAndTenantIdAndCaseId(disputeId, tenantId, caseId)
                .orElseThrow(() -> new NoSuchElementException("Dispute not found"));
    }

    private void event(
            CollectionCase collectionCase,
            String eventType,
            String entityType,
            UUID entityId,
            String actor,
            String summary,
            Instant at) {
        events.save(
                new CollectionEvent(
                        collectionCase.getTenantId(),
                        collectionCase.getId(),
                        eventType,
                        entityType,
                        entityId,
                        at,
                        actor,
                        summary));
    }

    private static void requireVersion(long actual, long expected, String resource) {
        if (actual != expected) {
            throw new BusinessConflictException("VERSION_CONFLICT", resource + " version conflict");
        }
    }

    private static BusinessConflictException invalidTransition(IllegalStateException ex) {
        return new BusinessConflictException("INVALID_STATE_TRANSITION", ex.getMessage());
    }
}
