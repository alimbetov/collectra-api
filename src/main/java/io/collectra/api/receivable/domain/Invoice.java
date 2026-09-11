package io.collectra.api.receivable.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "invoices",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_invoice_tenant_external",
                        columnNames = {"tenant_id", "external_id"}))
public class Invoice extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "contract_id")
    private UUID contractId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(name = "invoice_number", nullable = false, length = 120)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "original_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "outstanding_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;

    @Column(name = "document_file_id")
    private UUID documentFileId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private JsonNode customFields;

    protected Invoice() {}

    public Invoice(
            UUID tenantId,
            UUID customerId,
            UUID contractId,
            String externalId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            BigDecimal originalAmount,
            String currency,
            UUID documentFileId,
            JsonNode customFields) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        this.contractId = contractId;
        this.externalId = required(externalId);
        this.invoiceNumber = required(invoiceNumber);
        this.invoiceDate = invoiceDate;
        this.dueDate = Objects.requireNonNull(dueDate);
        this.originalAmount = positive(originalAmount);
        this.paidAmount = BigDecimal.ZERO;
        this.outstandingAmount = this.originalAmount;
        this.currency = required(currency).toUpperCase(Locale.ROOT);
        this.paymentStatus = PaymentStatus.OPEN;
        this.documentFileId = documentFileId;
        this.customFields = customFields == null ? null : customFields.deepCopy();
    }

    public void apply(BigDecimal amount) {
        BigDecimal allocation = positive(amount);
        if (allocation.compareTo(outstandingAmount) > 0) {
            throw new IllegalArgumentException("Allocation exceeds invoice outstanding amount");
        }
        paidAmount = paidAmount.add(allocation);
        outstandingAmount = originalAmount.subtract(paidAmount);
        paymentStatus = outstandingAmount.signum() == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIALLY_PAID;
    }

    public boolean isOverdue(LocalDate today) {
        return paymentStatus != PaymentStatus.PAID
                && paymentStatus != PaymentStatus.CANCELLED
                && dueDate.isBefore(today)
                && outstandingAmount.signum() > 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getContractId() {
        return contractId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public UUID getDocumentFileId() {
        return documentFileId;
    }

    public JsonNode getCustomFields() {
        return customFields == null ? null : customFields.deepCopy();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
        return value.trim();
    }

    private static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return value;
    }
}
