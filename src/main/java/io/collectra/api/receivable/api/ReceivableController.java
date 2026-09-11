package io.collectra.api.receivable.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.*;
import io.collectra.api.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class ReceivableController {
 private final ReceivableService service; public ReceivableController(ReceivableService service){this.service=service;}
 @PostMapping("/api/v1/invoices") @ResponseStatus(HttpStatus.CREATED) public InvoiceResponse createInvoice(@Valid @RequestBody InvoiceRequest r){return InvoiceResponse.from(service.createInvoice(tenant(),r.customerId(),r.contractId(),r.externalId(),r.invoiceNumber(),r.invoiceDate(),r.dueDate(),r.originalAmount(),r.currency(),r.documentFileId(),r.customFields()));}
 @GetMapping("/api/v1/invoices") public List<InvoiceResponse> invoices(){return service.invoices(tenant()).stream().map(InvoiceResponse::from).toList();}
 @GetMapping("/api/v1/invoices/{id}") public InvoiceResponse invoice(@PathVariable UUID id){return InvoiceResponse.from(service.invoice(tenant(),id));}
 @PostMapping("/api/v1/payments") @ResponseStatus(HttpStatus.CREATED) public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest r){return PaymentResponse.from(service.createPayment(tenant(),r.customerId(),r.externalId(),r.paymentDate(),r.amount(),r.currency(),r.paymentReference(),r.source(),r.customFields()));}
 @GetMapping("/api/v1/payments") public List<PaymentResponse> payments(){return service.payments(tenant()).stream().map(PaymentResponse::from).toList();}
 @GetMapping("/api/v1/payments/{id}") public PaymentResponse payment(@PathVariable UUID id){return PaymentResponse.from(service.payment(tenant(),id));}
 @PostMapping("/api/v1/payments/{id}/allocations") @ResponseStatus(HttpStatus.CREATED) public AllocationResponse allocate(@PathVariable UUID id,@Valid @RequestBody AllocationRequest r){PaymentAllocation v=service.allocate(tenant(),id,r.invoiceId(),r.amount());return new AllocationResponse(v.getId(),v.getPaymentId(),v.getInvoiceId(),v.getAmount());}
 @GetMapping("/api/v1/payments/{id}/allocations") public List<AllocationResponse> allocations(@PathVariable UUID id){return service.allocations(tenant(),id).stream().map(v->new AllocationResponse(v.getId(),v.getPaymentId(),v.getInvoiceId(),v.getAmount())).toList();}
 private UUID tenant(){return TenantContext.requireTenantId();}
 public record InvoiceRequest(@NotNull UUID customerId,UUID contractId,@NotBlank @Size(max=120) String externalId,@NotBlank @Size(max=120) String invoiceNumber,LocalDate invoiceDate,@NotNull LocalDate dueDate,@NotNull @DecimalMin("0.0001") BigDecimal originalAmount,@NotBlank @Size(min=3,max=3) String currency,UUID documentFileId,JsonNode customFields){}
 public record PaymentRequest(@NotNull UUID customerId,@NotBlank @Size(max=120) String externalId,@NotNull LocalDate paymentDate,@NotNull @DecimalMin("0.0001") BigDecimal amount,@NotBlank @Size(min=3,max=3) String currency,@Size(max=200) String paymentReference,@Size(max=80) String source,JsonNode customFields){}
 public record AllocationRequest(@NotNull UUID invoiceId,@NotNull @DecimalMin("0.0001") BigDecimal amount){}
 public record InvoiceResponse(UUID id,UUID customerId,UUID contractId,String externalId,String invoiceNumber,LocalDate invoiceDate,LocalDate dueDate,BigDecimal originalAmount,BigDecimal paidAmount,BigDecimal outstandingAmount,String currency,PaymentStatus paymentStatus,boolean overdue,UUID documentFileId,JsonNode customFields){static InvoiceResponse from(Invoice v){return new InvoiceResponse(v.getId(),v.getCustomerId(),v.getContractId(),v.getExternalId(),v.getInvoiceNumber(),v.getInvoiceDate(),v.getDueDate(),v.getOriginalAmount(),v.getPaidAmount(),v.getOutstandingAmount(),v.getCurrency(),v.getPaymentStatus(),v.isOverdue(LocalDate.now()),v.getDocumentFileId(),v.getCustomFields());}}
 public record PaymentResponse(UUID id,UUID customerId,String externalId,LocalDate paymentDate,BigDecimal amount,String currency,String paymentReference,String source,JsonNode customFields){static PaymentResponse from(Payment v){return new PaymentResponse(v.getId(),v.getCustomerId(),v.getExternalId(),v.getPaymentDate(),v.getAmount(),v.getCurrency(),v.getPaymentReference(),v.getSource(),v.getCustomFields());}}
 public record AllocationResponse(UUID id,UUID paymentId,UUID invoiceId,BigDecimal amount){}
}
