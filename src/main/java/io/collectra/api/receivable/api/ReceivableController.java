package io.collectra.api.receivable.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import io.collectra.api.receivable.domain.PaymentAllocation;
import io.collectra.api.receivable.domain.PaymentStatus;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class ReceivableController {
    private final ReceivableService service;

    public ReceivableController(ReceivableService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceResponse createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return InvoiceResponse.from(
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
    public List<InvoiceResponse> invoices() {
        return service.invoices(tenant()).stream().map(InvoiceResponse::from).toList();
    }

    @GetMapping("/api/v1/invoices/{id}")
    public InvoiceResponse invoice(@PathVariable UUID id) {
        return InvoiceResponse.from(service.invoice(tenant(), id));
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
    public List<PaymentResponse> payments() {
        return service.payments(tenant()).stream().map(PaymentResponse::from).toList();
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
                service.allocate(tenant(), id, request.invoiceId(), request.amount());
        return AllocationResponse.from(value);
    }

    @GetMapping("/api/v1/payments/{id}/allocations")
    public List<AllocationResponse> allocations(@PathVariable UUID id) {
        return service.allocations(tenant(), id).stream().map(AllocationResponse::from).toList();
    }

    private UUID tenant() {
        return TenantContext.requireTenantId();
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
            @NotNull UUID invoiceId,
            @NotNull @DecimalMin("0.0001") BigDecimal amount) {}

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
            UUID documentFileId,
            JsonNode customFields) {
        static InvoiceResponse from(Invoice value) {
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
                    value.isOverdue(LocalDate.now()),
                    value.getDocumentFileId(),
                    value.getCustomFields());
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
            JsonNode customFields) {
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
                    value.getCustomFields());
        }
    }

    public record AllocationResponse(
            UUID id, UUID paymentId, UUID invoiceId, BigDecimal amount) {
        static AllocationResponse from(PaymentAllocation value) {
            return new AllocationResponse(
                    value.getId(), value.getPaymentId(), value.getInvoiceId(), value.getAmount());
        }
    }
}
