package io.collectra.api.receivable.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.receivable.domain.*;
import io.collectra.api.receivable.infrastructure.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivableService {
 private final InvoiceRepository invoices; private final PaymentRepository payments; private final PaymentAllocationRepository allocations; private final CustomerService customers;
 public ReceivableService(InvoiceRepository invoices,PaymentRepository payments,PaymentAllocationRepository allocations,CustomerService customers){this.invoices=invoices;this.payments=payments;this.allocations=allocations;this.customers=customers;}
 @Transactional public Invoice createInvoice(UUID tenantId,UUID customerId,UUID contractId,String externalId,String invoiceNumber,LocalDate invoiceDate,LocalDate dueDate,BigDecimal amount,String currency,UUID documentFileId,JsonNode customFields){customers.get(tenantId,customerId);invoices.findByTenantIdAndExternalId(tenantId,externalId.trim()).ifPresent(v->{throw new IllegalArgumentException("Invoice externalId already exists");});return invoices.save(new Invoice(tenantId,customerId,contractId,externalId,invoiceNumber,invoiceDate,dueDate,amount,currency,documentFileId,customFields));}
 @Transactional(readOnly=true) public Invoice invoice(UUID tenantId,UUID id){return invoices.findByIdAndTenantId(id,tenantId).orElseThrow(()->new NoSuchElementException("Invoice not found"));}
 @Transactional(readOnly=true) public List<Invoice> invoices(UUID tenantId){return invoices.findAllByTenantIdOrderByCreatedAtDesc(tenantId);}
 @Transactional public Payment createPayment(UUID tenantId,UUID customerId,String externalId,LocalDate paymentDate,BigDecimal amount,String currency,String reference,String source,JsonNode customFields){customers.get(tenantId,customerId);payments.findByTenantIdAndExternalId(tenantId,externalId.trim()).ifPresent(v->{throw new IllegalArgumentException("Payment externalId already exists");});return payments.save(new Payment(tenantId,customerId,externalId,paymentDate,amount,currency,reference,source,customFields));}
 @Transactional(readOnly=true) public Payment payment(UUID tenantId,UUID id){return payments.findByIdAndTenantId(id,tenantId).orElseThrow(()->new NoSuchElementException("Payment not found"));}
 @Transactional(readOnly=true) public List<Payment> payments(UUID tenantId){return payments.findAllByTenantIdOrderByCreatedAtDesc(tenantId);}
 @Transactional public PaymentAllocation allocate(UUID tenantId,UUID paymentId,UUID invoiceId,BigDecimal amount){Payment p=payment(tenantId,paymentId);Invoice i=invoice(tenantId,invoiceId);if(!p.getCustomerId().equals(i.getCustomerId()))throw new IllegalArgumentException("Payment and invoice customers differ");if(!p.getCurrency().equals(i.getCurrency()))throw new IllegalArgumentException("Payment and invoice currencies differ");BigDecimal already=allocations.allocated(tenantId,paymentId);if(already.add(amount).compareTo(p.getAmount())>0)throw new IllegalArgumentException("Allocation exceeds payment amount");i.apply(amount);return allocations.save(new PaymentAllocation(tenantId,paymentId,invoiceId,amount));}
 @Transactional(readOnly=true) public List<PaymentAllocation> allocations(UUID tenantId,UUID paymentId){payment(tenantId,paymentId);return allocations.findAllByTenantIdAndPaymentId(tenantId,paymentId);}
}
