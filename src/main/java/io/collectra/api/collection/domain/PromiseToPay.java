package io.collectra.api.collection.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "promises_to_pay")
public class PromiseToPay extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "promised_date", nullable = false)
    private LocalDate promisedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromiseToPayStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected PromiseToPay() {}

    public PromiseToPay(
            UUID tenantId,
            UUID caseId,
            BigDecimal amount,
            String currency,
            LocalDate promisedDate) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.caseId = Objects.requireNonNull(caseId);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.amount = amount;
        this.currency = Objects.requireNonNull(currency).trim().toUpperCase(Locale.ROOT);
        this.promisedDate = Objects.requireNonNull(promisedDate);
        this.status = PromiseToPayStatus.ACTIVE;
    }

    public void fulfill(Instant at) { transition(PromiseToPayStatus.FULFILLED, at); }
    public void breakPromise(Instant at) { transition(PromiseToPayStatus.BROKEN, at); }
    public void cancel(Instant at) { transition(PromiseToPayStatus.CANCELLED, at); }

    private void transition(PromiseToPayStatus target, Instant at) {
        if (status != PromiseToPayStatus.ACTIVE) {
            throw new IllegalStateException("Promise to pay is already terminal");
        }
        status = target;
        resolvedAt = Objects.requireNonNull(at);
    }

    public boolean isOverdue(LocalDate businessDate) {
        return status == PromiseToPayStatus.ACTIVE && promisedDate.isBefore(businessDate);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCaseId() { return caseId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDate getPromisedDate() { return promisedDate; }
    public PromiseToPayStatus getStatus() { return status; }
    public Instant getResolvedAt() { return resolvedAt; }
}
