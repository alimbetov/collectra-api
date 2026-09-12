package io.collectra.api.collection.api;

import io.collectra.api.collection.application.CollectionQueryService;
import io.collectra.api.collection.application.CollectionService;
import io.collectra.api.collection.domain.CollectionAction;
import io.collectra.api.collection.domain.CollectionCase;
import io.collectra.api.collection.domain.CollectionCaseStatus;
import io.collectra.api.collection.domain.CollectionCloseReason;
import io.collectra.api.collection.domain.CollectionEvent;
import io.collectra.api.collection.domain.CollectionPriority;
import io.collectra.api.collection.domain.Dispute;
import io.collectra.api.collection.domain.PromiseToPay;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collection-cases")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class CollectionController {
    private final CollectionService service;
    private final CollectionQueryService queries;
    private final Clock clock;
    private final ZoneId businessZone;

    public CollectionController(
            CollectionService service, CollectionQueryService queries, Clock clock, ZoneId businessZone) {
        this.service = service;
        this.queries = queries;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @GetMapping
    public CollectionQueryService.CasePage list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) CollectionCaseStatus status,
            @RequestParam(required = false) CollectionPriority priority,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(CollectionQueryService.MAX_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.list(tenant(), customerId, invoiceId, status, priority, assignedTo, page, size, sort);
    }

    @GetMapping("/{caseId}")
    public CaseResponse get(@PathVariable UUID caseId) {
        return CaseResponse.from(service.getCase(tenant(), caseId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse create(@Valid @RequestBody CaseCreateRequest request) {
        return CaseResponse.from(
                service.createCase(
                        tenant(),
                        request.customerId(),
                        request.invoiceId(),
                        request.priority(),
                        request.assignedTo(),
                        actor()));
    }

    @PutMapping("/{caseId}")
    public CaseResponse update(
            @PathVariable UUID caseId, @Valid @RequestBody CaseUpdateRequest request) {
        return CaseResponse.from(
                service.updateCase(
                        tenant(),
                        caseId,
                        request.version(),
                        request.priority(),
                        request.assignedTo(),
                        actor()));
    }

    @PostMapping("/{caseId}/start")
    public CaseResponse start(@PathVariable UUID caseId, @Valid @RequestBody VersionRequest request) {
        return CaseResponse.from(service.start(tenant(), caseId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/hold")
    public CaseResponse hold(@PathVariable UUID caseId, @Valid @RequestBody VersionRequest request) {
        return CaseResponse.from(service.hold(tenant(), caseId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/close")
    public CaseResponse close(
            @PathVariable UUID caseId, @Valid @RequestBody CloseRequest request) {
        return CaseResponse.from(
                service.close(tenant(), caseId, request.version(), request.reason(), actor()));
    }

    @PostMapping("/{caseId}/promises")
    @ResponseStatus(HttpStatus.CREATED)
    public PromiseResponse createPromise(
            @PathVariable UUID caseId, @Valid @RequestBody PromiseCreateRequest request) {
        return promise(
                service.createPromise(
                        tenant(),
                        caseId,
                        request.amount(),
                        request.currency(),
                        request.promisedDate(),
                        actor()));
    }

    @GetMapping("/{caseId}/promises")
    public List<PromiseResponse> promises(@PathVariable UUID caseId) {
        return service.promises(tenant(), caseId).stream().map(this::promise).toList();
    }

    @PostMapping("/{caseId}/promises/{promiseId}/fulfill")
    public PromiseResponse fulfillPromise(
            @PathVariable UUID caseId,
            @PathVariable UUID promiseId,
            @Valid @RequestBody VersionRequest request) {
        return promise(service.fulfillPromise(tenant(), caseId, promiseId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/promises/{promiseId}/break")
    public PromiseResponse breakPromise(
            @PathVariable UUID caseId,
            @PathVariable UUID promiseId,
            @Valid @RequestBody VersionRequest request) {
        return promise(service.breakPromise(tenant(), caseId, promiseId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/promises/{promiseId}/cancel")
    public PromiseResponse cancelPromise(
            @PathVariable UUID caseId,
            @PathVariable UUID promiseId,
            @Valid @RequestBody VersionRequest request) {
        return promise(service.cancelPromise(tenant(), caseId, promiseId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeResponse createDispute(
            @PathVariable UUID caseId, @Valid @RequestBody DisputeCreateRequest request) {
        return DisputeResponse.from(
                service.createDispute(
                        tenant(), caseId, request.reason(), request.description(), actor()));
    }

    @GetMapping("/{caseId}/disputes")
    public List<DisputeResponse> disputes(@PathVariable UUID caseId) {
        return service.disputes(tenant(), caseId).stream().map(DisputeResponse::from).toList();
    }

    @PostMapping("/{caseId}/disputes/{disputeId}/resolve")
    public DisputeResponse resolveDispute(
            @PathVariable UUID caseId,
            @PathVariable UUID disputeId,
            @Valid @RequestBody DisputeResolveRequest request) {
        return DisputeResponse.from(
                service.resolveDispute(
                        tenant(),
                        caseId,
                        disputeId,
                        request.version(),
                        request.resolutionCode(),
                        request.summary(),
                        actor()));
    }

    @PostMapping("/{caseId}/disputes/{disputeId}/cancel")
    public DisputeResponse cancelDispute(
            @PathVariable UUID caseId,
            @PathVariable UUID disputeId,
            @Valid @RequestBody VersionRequest request) {
        return DisputeResponse.from(
                service.cancelDispute(tenant(), caseId, disputeId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ActionResponse createAction(
            @PathVariable UUID caseId, @Valid @RequestBody ActionCreateRequest request) {
        return action(
                service.createAction(
                        tenant(),
                        caseId,
                        request.actionType(),
                        request.description(),
                        request.dueAt(),
                        request.priority(),
                        actor()));
    }

    @GetMapping("/{caseId}/actions")
    public List<ActionResponse> actions(@PathVariable UUID caseId) {
        return service.actions(tenant(), caseId).stream().map(this::action).toList();
    }

    @PostMapping("/{caseId}/actions/{actionId}/complete")
    public ActionResponse completeAction(
            @PathVariable UUID caseId,
            @PathVariable UUID actionId,
            @Valid @RequestBody VersionRequest request) {
        return action(service.completeAction(tenant(), caseId, actionId, request.version(), actor()));
    }

    @PostMapping("/{caseId}/actions/{actionId}/cancel")
    public ActionResponse cancelAction(
            @PathVariable UUID caseId,
            @PathVariable UUID actionId,
            @Valid @RequestBody VersionRequest request) {
        return action(service.cancelAction(tenant(), caseId, actionId, request.version(), actor()));
    }

    @GetMapping("/{caseId}/timeline")
    public List<TimelineItem> timeline(@PathVariable UUID caseId) {
        return service.timeline(tenant(), caseId).stream().map(TimelineItem::from).toList();
    }

    private PromiseResponse promise(PromiseToPay value) {
        LocalDate businessDate = LocalDate.now(clock.withZone(businessZone));
        return PromiseResponse.from(value, value.isOverdue(businessDate), businessDate);
    }

    private ActionResponse action(CollectionAction value) {
        Instant now = Instant.now(clock);
        return ActionResponse.from(value, value.isOverdue(now), now);
    }

    private UUID tenant() { return TenantContext.requireTenantId(); }
    private String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }

    public record CaseCreateRequest(
            @NotNull UUID customerId,
            @NotNull UUID invoiceId,
            CollectionPriority priority,
            UUID assignedTo) {}

    public record CaseUpdateRequest(
            @Min(0) long version, CollectionPriority priority, UUID assignedTo) {}

    public record VersionRequest(@Min(0) long version) {}
    public record CloseRequest(@Min(0) long version, @NotNull CollectionCloseReason reason) {}

    public record PromiseCreateRequest(
            @NotNull @DecimalMin("0.0001") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull LocalDate promisedDate) {}

    public record DisputeCreateRequest(
            @NotBlank @Size(max = 80) String reason, @Size(max = 1000) String description) {}

    public record DisputeResolveRequest(
            @Min(0) long version,
            @NotBlank @Size(max = 80) String resolutionCode,
            @Size(max = 1000) String summary) {}

    public record ActionCreateRequest(
            @NotBlank @Size(max = 80) String actionType,
            @Size(max = 1000) String description,
            @NotNull Instant dueAt,
            CollectionPriority priority) {}

    public record CaseResponse(
            UUID id,
            UUID customerId,
            UUID invoiceId,
            CollectionCaseStatus status,
            CollectionPriority priority,
            UUID assignedTo,
            Instant openedAt,
            Instant closedAt,
            CollectionCloseReason closeReason,
            long version) {
        static CaseResponse from(CollectionCase value) {
            return new CaseResponse(
                    value.getId(),
                    value.getCustomerId(),
                    value.getInvoiceId(),
                    value.getStatus(),
                    value.getPriority(),
                    value.getAssignedTo(),
                    value.getOpenedAt(),
                    value.getClosedAt(),
                    value.getCloseReason(),
                    value.getVersion());
        }
    }

    public record PromiseResponse(
            UUID id,
            BigDecimal amount,
            String currency,
            LocalDate promisedDate,
            String status,
            boolean overdue,
            LocalDate businessDate,
            Instant resolvedAt,
            long version) {
        static PromiseResponse from(PromiseToPay value, boolean overdue, LocalDate businessDate) {
            return new PromiseResponse(
                    value.getId(),
                    value.getAmount(),
                    value.getCurrency(),
                    value.getPromisedDate(),
                    value.getStatus().name(),
                    overdue,
                    businessDate,
                    value.getResolvedAt(),
                    value.getVersion());
        }
    }

    public record DisputeResponse(
            UUID id,
            String reason,
            String description,
            String status,
            String resolutionCode,
            String resolutionSummary,
            Instant resolvedAt,
            String resolvedBy,
            long version) {
        static DisputeResponse from(Dispute value) {
            return new DisputeResponse(
                    value.getId(),
                    value.getReason(),
                    value.getDescription(),
                    value.getStatus().name(),
                    value.getResolutionCode(),
                    value.getResolutionSummary(),
                    value.getResolvedAt(),
                    value.getResolvedBy(),
                    value.getVersion());
        }
    }

    public record ActionResponse(
            UUID id,
            String actionType,
            String description,
            Instant dueAt,
            CollectionPriority priority,
            String status,
            boolean overdue,
            Instant asOf,
            long version) {
        static ActionResponse from(CollectionAction value, boolean overdue, Instant asOf) {
            return new ActionResponse(
                    value.getId(),
                    value.getActionType(),
                    value.getDescription(),
                    value.getDueAt(),
                    value.getPriority(),
                    value.getStatus().name(),
                    overdue,
                    asOf,
                    value.getVersion());
        }
    }

    public record TimelineItem(
            UUID eventId,
            String eventType,
            String entityType,
            UUID entityId,
            Instant eventAt,
            String actor,
            String summary) {
        static TimelineItem from(CollectionEvent value) {
            return new TimelineItem(
                    value.getId(),
                    value.getEventType(),
                    value.getEntityType(),
                    value.getEntityId(),
                    value.getEventAt(),
                    value.getActor(),
                    value.getSummary());
        }
    }
}
