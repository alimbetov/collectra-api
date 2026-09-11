package io.collectra.api.receivable.domain;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
@Entity @Table(name="payment_allocations")
public class PaymentAllocation extends AuditableEntity {
 @Id private UUID id; @Column(name="tenant_id",nullable=false) private UUID tenantId; @Column(name="payment_id",nullable=false) private UUID paymentId; @Column(name="invoice_id",nullable=false) private UUID invoiceId; @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
 protected PaymentAllocation(){} public PaymentAllocation(UUID tenantId,UUID paymentId,UUID invoiceId,BigDecimal amount){this.id=UUID.randomUUID();this.tenantId=Objects.requireNonNull(tenantId);this.paymentId=Objects.requireNonNull(paymentId);this.invoiceId=Objects.requireNonNull(invoiceId);if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("amount must be positive");this.amount=amount;}
 public UUID getId(){return id;} public UUID getPaymentId(){return paymentId;} public UUID getInvoiceId(){return invoiceId;} public BigDecimal getAmount(){return amount;}
}
