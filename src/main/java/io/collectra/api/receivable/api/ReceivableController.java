package io.collectra.api.receivable.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.receivable.application.ReceivableQueryService;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.AllocationStatus;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import io.collectra.api.receivable.domain.PaymentAllocation;
import io.collectra.api.receivable.domain.PaymentStatus;
import io.collectra.api.shared.api.DecimalString;
import io.collectra.api.shared.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class ReceivableController {
    private final ReceivableService service;
    private final ReceivableQueryService queries;
    private final Clock clock;
    private final ZoneId businessZone;

    public ReceivableController(
            ReceivableService service,
            ReceivableQueryService queries,
            Clock clock,
            ZoneId businessZone) {
        this.service = service;
        this.queries = queries;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @PostMapping("/api/v1/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return invoiceResponse(
                service.createInvoice(
                        tenant(),
                        request.customerId(),
                        request.contractId(),
                        request.externalId(),
                        request.invoiceNumber(),
                        request.invoiceDate(),
                        request.dueDate(),
                        request.originalAmount().positiveValue("originalAmount"),
                        request.currency(),
                        request.documentFileId(),
                        request.customFields()));
    }

    @GetMapping("/api/v1/invoices")
    public InvoicePageResponse invoices(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LocalDate issuedFrom,
            @RequestParam(required = false) LocalDate issuedTo,
            @RequestParam(required = false) LocalDate dueFrom,
            @RequestParam(required = false) LocalDate dueTo,
            @RequestParam(required = false) Boolean overdue,
            @Parameter(schema = @Schema(type = "string", pattern = DecimalString.PATTERN))
                    @RequestParam(required = false)
                    String amountMin,
            @Parameter(schema = @Schema(type = "string", pattern = DecimalString.PATTERN))
                    @RequestParam(required = false)
                    String amountMax,
            @Parameter(schema = @Schema(type = "string", pattern = DecimalString.PATTERN))
                    @RequestParam(required = false)
                    String outstandingMin,
            @Parameter(schema = @Schema(type = "string", pattern = DecimalString.PATTERN))
                    @RequestParam(required = false)
                    String outstandingMax,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ReceivableQueryService.MAX_SIZE)
                    int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return InvoicePageResponse.from(
                queries.invoices(
                        tenant(),
                        customerId,
                        contractId,
                        paymentStatus,
                        currency,
                        invoiceNumber,
                        externalId,
                        search,
                        issuedFrom,
                        issuedTo,
                        dueFrom,
                        dueTo,
                        overdue,
                        DecimalString.parseNullable(amountMin),
                        DecimalString.parseNullable(amountMax),
                        DecimalString.parseNullable(outstandingMin),
                        DecimalString.parseNullable(outstandingMax),
                        page,
                        size,
                        sort));
    }

    @GetMapping("/api/v1/invoices/{id}")
    public InvoiceResponse invoice(@PathVariable UUID id) {
        return invoiceResponse(service.invoice(tenant(), id));
    }

    @PostMapping("/api/v1/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request) {
        return PaymentResponse.from(
                service.createPayment(
                        tenant(),
                        request.customerId(),
                        request.externalId(),
                        request.paymentDate(),
                        request.amount().positiveValue("amount"),
                        request.currency(),
                        request.paymentReference(),
                        request.source(),
                        request.customFields()));
    }

    @GetMapping("/api/v1/payments")
    public PaymentPageResponse payments(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) LocalDate paymentFrom,
            @RequestParam(required = false) LocalDate paymentTo,
            @Parameter(schema = @Schema(type = "string", pattern = DecimalString.PATTERN))
                    @RequestParam(required = false)
                    String amountMin,
            @Parameter(schema = @Schema(type = "string", pattern = DecimalString.PATTERN))
                    @RequestParam(required = false)
                    String amountMax,
            @RequestParam(required = false) Boolean unallocatedOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ReceivableQueryService.MAX_SIZE)
                    int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return PaymentPageResponse.from(
                queries.payments(
                        tenant(),
                        customerId,
                        invoiceId,
                        currency,
                        paymentReference,
                        externalId,
                        paymentFrom,
                        paymentTo,
                        DecimalString.parseNullable(amountMin),
                        DecimalString.parseNullable(amountMax),
                        unallocatedOnly,
                        search,
                        page,
                        size,
                        sort));
    }

    @GetMapping("/api/v1/payments/{id}")
    public PaymentResponse payment(@PathVariable UUID id) {
        return PaymentResponse.from(service.payment(tenant(), id));
    }

    @PostMapping("/api/v1/payments/{id}/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationResponse allocate(
            @PathVariable UUID id, @Valid @RequestBody AllocationRequest request) {
        PaymentAllocation value =
                service.allocate(
                        tenant(),
                        id,
                        request.commandId(),
                        request.invoiceId(),
                        request.amount().positiveValue("amount"));
        return AllocationResponse.from(value);
    }

    @GetMapping("/api/v1/payments/{id}/allocations")
    public AllocationPageResponse allocations(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ReceivableService.MAX_ALLOCATION_PAGE_SIZE)
                    int size) {
        return AllocationPageResponse.from(service.allocations(tenant(), id, page, size));
    }

    @GetMapping("/api/v1/invoices/{id}/allocations")
    public AllocationPageResponse invoiceAllocations(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ReceivableService.MAX_ALLOCATION_PAGE_SIZE)
                    int size) {
        return AllocationPageResponse.from(service.invoiceAllocations(tenant(), id, page, size));
    }

    @PostMapping("/api/v1/payments/{paymentId}/allocations/{allocationId}/reverse")
    public AllocationResponse reverseAllocation(
            @PathVariable UUID paymentId,
            @PathVariable UUID allocationId,
            @Valid @RequestBody AllocationReversalRequest request) {
        return AllocationResponse.from(
                service.reverseAllocation(
                        tenant(),
                        paymentId,
                        allocationId,
                        request.version(),
                        request.reason(),
                        actor()));
    }

    private InvoiceResponse invoiceResponse(Invoice value) {
        LocalDate businessDate = LocalDate.now(clock.withZone(businessZone));
        boolean overdue = value.isOverdue(businessDate);
        long daysOverdue = overdue ? ChronoUnit.DAYS.between(value.getDueDate(), businessDate) : 0;
        return InvoiceResponse.from(value, overdue, daysOverdue, businessDate);
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
    }

    private String actor() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public record InvoiceRequest(
            @NotNull UUID customerId,
            UUID contractId,
            @NotBlank @Size(max = 120) String externalId,
            @NotBlank @Size(max = 120) String invoiceNumber,
            LocalDate invoiceDate,
            @NotNull LocalDate dueDate,
            @NotNull @Schema(type = "string", pattern = DecimalString.PATTERN)
                    DecimalString originalAmount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            UUID documentFileId,
            JsonNode customFields) {}

    public record PaymentRequest(
            @NotNull UUID customerId,
            @NotBlank @Size(max = 120) String externalId,
            @NotNull LocalDate paymentDate,
            @NotNull @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 200) String paymentReference,
            @Size(max = 80) String source,
            JsonNode customFields) {}

    public record AllocationRequest(
            @NotNull UUID commandId,
            @NotNull UUID invoiceId,
            @NotNull @Schema(type = "string", pattern = DecimalString.PATTERN)
                    DecimalString amount) {}

    public record AllocationReversalRequest(
            @Min(0) long version, @NotBlank @Size(max = 200) String reason) {}

    public record InvoiceResponse(
            UUID id,
            UUID customerId,
            UUID contractId,
            String externalId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString originalAmount,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString paidAmount,
            @Schema(type = "string", pattern = DecimalString.PATTERN)
                    DecimalString outstandingAmount,
            String currency,
            PaymentStatus paymentStatus,
            boolean overdue,
            long daysOverdue,
            LocalDate businessDate,
            UUID documentFileId,
            JsonNode customFields,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static InvoiceResponse from(
                Invoice value, boolean overdue, long daysOverdue, LocalDate businessDate) {
            return new InvoiceResponse(
                    value.getId(),
                    value.getCustomerId(),
                    value.getContractId(),
                    value.getExternalId(),
                    value.getInvoiceNumber(),
                    value.getInvoiceDate(),
                    value.getDueDate(),
                    DecimalString.of(value.getOriginalAmount()),
                    DecimalString.of(value.getPaidAmount()),
                    DecimalString.of(value.getOutstandingAmount()),
                    value.getCurrency(),
                    value.getPaymentStatus(),
                    overdue,
                    daysOverdue,
                    businessDate,
                    value.getDocumentFileId(),
                    value.getCustomFields(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }

    public record PaymentResponse(
            UUID id,
            UUID customerId,
            String externalId,
            LocalDate paymentDate,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amount,
            String currency,
            String paymentReference,
            String source,
            JsonNode customFields,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        static PaymentResponse from(Payment value) {
            return new PaymentResponse(
                    value.getId(),
                    value.getCustomerId(),
                    value.getExternalId(),
                    value.getPaymentDate(),
                    DecimalString.of(value.getAmount()),
                    value.getCurrency(),
                    value.getPaymentReference(),
                    value.getSource(),
                    value.getCustomFields(),
                    value.getCreatedAt(),
                    value.getUpdatedAt(),
                    value.getVersion());
        }
    }

    public record AllocationResponse(
            UUID id,
            UUID paymentId,
            UUID invoiceId,
            UUID commandId,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amount,
            AllocationStatus status,
            Instant reversedAt,
            String reversedBy,
            String reversalReason,
            Instant createdAt,
            long version) {
        static AllocationResponse from(PaymentAllocation value) {
            return new AllocationResponse(
                    value.getId(),
                    value.getPaymentId(),
                    value.getInvoiceId(),
                    value.getCommandId(),
                    DecimalString.of(value.getAmount()),
                    value.getStatus(),
                    value.getReversedAt(),
                    value.getReversedBy(),
                    value.getReversalReason(),
                    value.getCreatedAt(),
                    value.getVersion());
        }
    }

    @Schema(name = "AllocationPage")
    public record AllocationPageResponse(
            List<AllocationResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {
        static AllocationPageResponse from(Page<PaymentAllocation> value) {
            return new AllocationPageResponse(
                    value.getContent().stream().map(AllocationResponse::from).toList(),
                    value.getNumber(),
                    value.getSize(),
                    value.getTotalElements(),
                    value.getTotalPages(),
                    value.hasNext());
        }
    }

    @Schema(name = "InvoiceItem")
    public record InvoiceItemResponse(
            UUID id,
            UUID customerId,
            String customerDisplayName,
            UUID contractId,
            String contractNumber,
            String externalId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString originalAmount,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString paidAmount,
            @Schema(type = "string", pattern = DecimalString.PATTERN)
                    DecimalString outstandingAmount,
            String currency,
            PaymentStatus paymentStatus,
            boolean overdue,
            long daysOverdue) {
        static InvoiceItemResponse from(ReceivableQueryService.InvoiceItem value) {
            return new InvoiceItemResponse(
                    value.id(),
                    value.customerId(),
                    value.customerDisplayName(),
                    value.contractId(),
                    value.contractNumber(),
                    value.externalId(),
                    value.invoiceNumber(),
                    value.invoiceDate(),
                    value.dueDate(),
                    DecimalString.of(value.originalAmount()),
                    DecimalString.of(value.paidAmount()),
                    DecimalString.of(value.outstandingAmount()),
                    value.currency(),
                    value.paymentStatus(),
                    value.overdue(),
                    value.daysOverdue());
        }
    }

    @Schema(name = "InvoicePage")
    public record InvoicePageResponse(
            List<InvoiceItemResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            LocalDate businessDate) {
        static InvoicePageResponse from(ReceivableQueryService.InvoicePage value) {
            return new InvoicePageResponse(
                    value.items().stream().map(InvoiceItemResponse::from).toList(),
                    value.page(),
                    value.size(),
                    value.totalElements(),
                    value.totalPages(),
                    value.hasNext(),
                    value.businessDate());
        }
    }

    @Schema(name = "PaymentItem")
    public record PaymentItemResponse(
            UUID id,
            UUID customerId,
            String customerDisplayName,
            String externalId,
            LocalDate paymentDate,
            @Schema(type = "string", pattern = DecimalString.PATTERN) DecimalString amount,
            String currency,
            String paymentReference,
            String source) {
        static PaymentItemResponse from(ReceivableQueryService.PaymentItem value) {
            return new PaymentItemResponse(
                    value.id(),
                    value.customerId(),
                    value.customerDisplayName(),
                    value.externalId(),
                    value.paymentDate(),
                    DecimalString.of(value.amount()),
                    value.currency(),
                    value.paymentReference(),
                    value.source());
        }
    }

    @Schema(name = "PaymentPage")
    public record PaymentPageResponse(
            List<PaymentItemResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {
        static PaymentPageResponse from(ReceivableQueryService.PaymentPage value) {
            return new PaymentPageResponse(
                    value.items().stream().map(PaymentItemResponse::from).toList(),
                    value.page(),
                    value.size(),
                    value.totalElements(),
                    value.totalPages(),
                    value.hasNext());
        }
    }
}
