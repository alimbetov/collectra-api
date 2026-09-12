package io.collectra.api.receivable.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.receivable.application.ReceivableQueryService;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.AllocationStatus;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import io.collectra.api.receivable.domain.PaymentAllocation;
import io.collectra.api.receivable.domain.PaymentStatus;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
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
                        request.originalAmount(),
                        request.currency(),
                        request.documentFileId(),
                        request.customFields()));
    }

    @GetMapping("/api/v1/invoices")
    public ReceivableQueryService.InvoicePage invoices(
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
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) BigDecimal outstandingMin,
            @RequestParam(required = false) BigDecimal outstandingMax,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ReceivableQueryService.MAX_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.invoices(
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
                amountMin,
                amountMax,
                outstandingMin,
                outstandingMax,
                page,
                size,
                sort);
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
                        request.amount(),
                        request.currency(),
                        request.paymentReference(),
                        request.source(),
                        request.customFields()));
    }

    @GetMapping("/api/v1/payments")
    public ReceivableQueryService.PaymentPage payments(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID invoiceId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String paymentReference,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) LocalDate paymentFrom,
            @RequestParam(required = false) LocalDate paymentTo,
            @RequestParam(required = false) BigDecimal amountMin,
            @RequestParam(required = false) BigDecimal amountMax,
            @RequestParam(required = false) Boolean unallocatedOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(ReceivableQueryService.MAX_SIZE) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return queries.payments(
                tenant(),
                customerId,
                invoiceId,
                currency,
                paymentReference,
                externalId,
                paymentFrom,
                paymentTo,
                amountMin,
                amountMax,
                unallocatedOnly,
                search,
                page,
                size,
                sort);
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
                        request.amount());
        return AllocationResponse.from(value);
    }

    @GetMapping("/api/v1/payments/{id}/allocations")
    public List<AllocationResponse> allocations(@PathVariable UUID id) {
        return service.allocations(tenant(), id).stream().map(AllocationResponse::from).toList();
    }

    @GetMapping("/api/v1/invoices/{id}/allocations")
    public List<AllocationResponse> invoiceAllocations(@PathVariable UUID id) {
        return service.invoiceAllocations(tenant(), id).stream()
                .map(AllocationResponse::from)
                .toList();
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
            @NotNull @DecimalMin("0.0001") BigDecimal originalAmount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            UUID documentFileId,
            JsonNode customFields) {}

    public record PaymentRequest(
            @NotNull UUID customerId,
            @NotBlank @Size(max = 120) String externalId,
            @NotNull LocalDate paymentDate,
            @NotNull @DecimalMin("0.0001") BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 200) String paymentReference,
            @Size(max = 80) String source,
            JsonNode customFields) {}

    public record AllocationRequest(
            @NotNull UUID commandId,
            @NotNull UUID invoiceId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount) {}

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
            BigDecimal originalAmount,
            BigDecimal paidAmount,
            BigDecimal outstandingAmount,
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
                    value.getOriginalAmount(),
                    value.getPaidAmount(),
                    value.getOutstandingAmount(),
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
            BigDecimal amount,
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
                    value.getAmount(),
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
            BigDecimal amount,
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
                    value.getAmount(),
                    value.getStatus(),
                    value.getReversedAt(),
                    value.getReversedBy(),
                    value.getReversalReason(),
                    value.getCreatedAt(),
                    value.getVersion());
        }
    }
}
