package io.collectra.api.receivable.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.contract.application.ContractService;
import io.collectra.api.contract.domain.Contract;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.receivable.domain.AllocationStatus;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import io.collectra.api.receivable.domain.PaymentAllocation;
import io.collectra.api.receivable.infrastructure.InvoiceRepository;
import io.collectra.api.receivable.infrastructure.PaymentAllocationRepository;
import io.collectra.api.receivable.infrastructure.PaymentRepository;
import io.collectra.api.shared.error.BusinessConflictException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivableService {
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final CustomerService customers;
    private final ContractService contracts;
    private final Clock clock;

    public ReceivableService(
            InvoiceRepository invoices,
            PaymentRepository payments,
            PaymentAllocationRepository allocations,
            CustomerService customers,
            ContractService contracts,
            Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.allocations = allocations;
        this.customers = customers;
        this.contracts = contracts;
        this.clock = clock;
    }

    @Transactional
    public Invoice createInvoice(
            UUID tenantId,
            UUID customerId,
            UUID contractId,
            String externalId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal amount,
            String currency,
            UUID documentFileId,
            JsonNode customFields) {
        customers.get(tenantId, customerId);
        if (contractId != null) {
            Contract contract = contracts.get(tenantId, contractId);
            if (!contract.getCustomerId().equals(customerId)) {
                throw new BusinessConflictException(
                        "CUSTOMER_MISMATCH", "Contract belongs to another customer");
            }
        }
        String normalizedExternalId = externalId.trim();
        invoices.findByTenantIdAndExternalId(tenantId, normalizedExternalId)
                .ifPresent(
                        value -> {
                            throw new BusinessConflictException(
                                    "DUPLICATE_EXTERNAL_ID", "Invoice externalId already exists");
                        });
        return invoices.save(
                new Invoice(
                        tenantId,
                        customerId,
                        contractId,
                        normalizedExternalId,
                        invoiceNumber,
                        invoiceDate,
                        dueDate,
                        amount,
                        currency,
                        documentFileId,
                        customFields));
    }

    @Transactional(readOnly = true)
    public Invoice invoice(UUID tenantId, UUID id) {
        return invoices.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> findInvoiceByExternalId(UUID tenantId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return invoices.findByTenantIdAndExternalId(tenantId, externalId.trim());
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoices(UUID tenantId) {
        return invoices.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> invoicesByIds(UUID tenantId, Collection<UUID> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return List.of();
        }
        return invoices.findAllByTenantIdAndIdIn(tenantId, invoiceIds);
    }

    @Transactional(readOnly = true)
    public Page<Invoice> campaignCandidates(
            UUID tenantId,
            Collection<UUID> customerIds,
            BigDecimal amountFrom,
            BigDecimal amountTo,
            LocalDate dueDateFrom,
            LocalDate dueDateTo,
            Pageable pageable) {
        if (customerIds == null || customerIds.isEmpty()) {
            return invoices.findCampaignCandidates(
                    tenantId, amountFrom, amountTo, dueDateFrom, dueDateTo, pageable);
        }
        return invoices.findCampaignCandidatesForCustomers(
                tenantId, customerIds, amountFrom, amountTo, dueDateFrom, dueDateTo, pageable);
    }

    @Transactional
    public Payment createPayment(
            UUID tenantId,
            UUID customerId,
            String externalId,
            LocalDate paymentDate,
            BigDecimal amount,
            String currency,
            String reference,
            String source,
            JsonNode customFields) {
        customers.get(tenantId, customerId);
        String normalizedExternalId = externalId.trim();
        payments.findByTenantIdAndExternalId(tenantId, normalizedExternalId)
                .ifPresent(
                        value -> {
                            throw new BusinessConflictException(
                                    "DUPLICATE_EXTERNAL_ID", "Payment externalId already exists");
                        });
        return payments.save(
                new Payment(
                        tenantId,
                        customerId,
                        normalizedExternalId,
                        paymentDate,
                        amount,
                        currency,
                        reference,
                        source,
                        customFields));
    }

    @Transactional(readOnly = true)
    public Payment payment(UUID tenantId, UUID id) {
        return payments.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findPaymentByExternalId(UUID tenantId, String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return payments.findByTenantIdAndExternalId(tenantId, externalId.trim());
    }

    @Transactional(readOnly = true)
    public List<Payment> payments(UUID tenantId) {
        return payments.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public PaymentAllocation allocate(
            UUID tenantId, UUID paymentId, UUID invoiceId, BigDecimal amount) {
        return allocate(tenantId, paymentId, UUID.randomUUID(), invoiceId, amount);
    }

    @Transactional
    public PaymentAllocation allocate(
            UUID tenantId, UUID paymentId, UUID commandId, UUID invoiceId, BigDecimal amount) {
        PaymentAllocation replay =
                allocations.findByTenantIdAndCommandId(tenantId, commandId).orElse(null);
        if (replay != null) {
            if (replay.getPaymentId().equals(paymentId)
                    && replay.getInvoiceId().equals(invoiceId)
                    && replay.getAmount().compareTo(amount) == 0) {
                return replay;
            }
            throw new BusinessConflictException(
                    "IDEMPOTENCY_CONFLICT", "commandId was already used with another allocation");
        }

        Payment payment = lockPayment(tenantId, paymentId);
        Invoice invoice = lockInvoice(tenantId, invoiceId);
        if (!payment.getCustomerId().equals(invoice.getCustomerId())) {
            throw new BusinessConflictException(
                    "CUSTOMER_MISMATCH", "Payment and invoice customers differ");
        }
        if (!payment.getCurrency().equals(invoice.getCurrency())) {
            throw new BusinessConflictException(
                    "CURRENCY_MISMATCH", "Payment and invoice currencies differ");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessConflictException(
                    "INVALID_REQUEST", "Allocation amount must be positive");
        }

        BigDecimal alreadyAllocated =
                allocations.allocated(tenantId, paymentId, AllocationStatus.ACTIVE);
        if (alreadyAllocated.add(amount).compareTo(payment.getAmount()) > 0) {
            throw new BusinessConflictException(
                    "ALLOCATION_EXCEEDS_PAYMENT", "Allocation exceeds payment amount");
        }
        if (amount.compareTo(invoice.getOutstandingAmount()) > 0) {
            throw new BusinessConflictException(
                    "ALLOCATION_EXCEEDS_INVOICE", "Allocation exceeds invoice outstanding amount");
        }

        invoice.apply(amount);
        try {
            return allocations.saveAndFlush(
                    new PaymentAllocation(tenantId, paymentId, invoiceId, commandId, amount));
        } catch (DataIntegrityViolationException ex) {
            PaymentAllocation concurrent =
                    allocations
                            .findByTenantIdAndCommandId(tenantId, commandId)
                            .orElseThrow(() -> ex);
            if (concurrent.getPaymentId().equals(paymentId)
                    && concurrent.getInvoiceId().equals(invoiceId)
                    && concurrent.getAmount().compareTo(amount) == 0) {
                return concurrent;
            }
            throw new BusinessConflictException(
                    "IDEMPOTENCY_CONFLICT", "commandId was already used with another allocation");
        }
    }

    @Transactional
    public PaymentAllocation reverseAllocation(
            UUID tenantId,
            UUID paymentId,
            UUID allocationId,
            long version,
            String reason,
            String actor) {
        Payment payment = lockPayment(tenantId, paymentId);
        PaymentAllocation allocation =
                allocations
                        .findByIdAndTenantIdAndPaymentId(allocationId, tenantId, paymentId)
                        .orElseThrow(() -> new NoSuchElementException("Allocation not found"));
        if (allocation.getVersion() != version) {
            throw new BusinessConflictException("VERSION_CONFLICT", "Allocation version conflict");
        }
        if (allocation.getStatus() == AllocationStatus.REVERSED) {
            throw new BusinessConflictException(
                    "ALLOCATION_ALREADY_REVERSED", "Allocation already reversed");
        }
        Invoice invoice = lockInvoice(tenantId, allocation.getInvoiceId());
        if (!payment.getCustomerId().equals(invoice.getCustomerId())) {
            throw new BusinessConflictException(
                    "CUSTOMER_MISMATCH", "Payment and invoice customers differ");
        }
        invoice.reverseAllocation(allocation.getAmount());
        allocation.reverse(Instant.now(clock), actor, reason);
        return allocation;
    }

    @Transactional(readOnly = true)
    public List<PaymentAllocation> allocations(UUID tenantId, UUID paymentId) {
        payment(tenantId, paymentId);
        return allocations.findAllByTenantIdAndPaymentIdOrderByCreatedAtAsc(tenantId, paymentId);
    }

    @Transactional(readOnly = true)
    public List<PaymentAllocation> invoiceAllocations(UUID tenantId, UUID invoiceId) {
        invoice(tenantId, invoiceId);
        return allocations.findAllByTenantIdAndInvoiceIdOrderByCreatedAtAsc(tenantId, invoiceId);
    }

    private Payment lockPayment(UUID tenantId, UUID id) {
        return payments.lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }

    private Invoice lockInvoice(UUID tenantId, UUID id) {
        return invoices.lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    }
}
