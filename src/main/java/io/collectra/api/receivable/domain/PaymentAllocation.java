package io.collectra.api.receivable.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_allocations")
public class PaymentAllocation extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "command_id", nullable = false)
    private UUID commandId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllocationStatus status;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private String reversedBy;

    @Column(name = "reversal_reason", length = 200)
    private String reversalReason;

    protected PaymentAllocation() {}

    public PaymentAllocation(
            UUID tenantId, UUID paymentId, UUID invoiceId, UUID commandId, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.paymentId = Objects.requireNonNull(paymentId);
        this.invoiceId = Objects.requireNonNull(invoiceId);
        this.commandId = Objects.requireNonNull(commandId);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = amount;
        this.status = AllocationStatus.ACTIVE;
    }

    public void reverse(Instant reversedAt, String reversedBy, String reason) {
        if (status == AllocationStatus.REVERSED) {
            throw new IllegalStateException("Allocation already reversed");
        }
        this.status = AllocationStatus.REVERSED;
        this.reversedAt = Objects.requireNonNull(reversedAt);
        this.reversedBy = trim(reversedBy);
        this.reversalReason = required(reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public AllocationStatus getStatus() {
        return status;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReversedBy() {
        return reversedBy;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
