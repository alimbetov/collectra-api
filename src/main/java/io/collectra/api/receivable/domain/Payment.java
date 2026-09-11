package io.collectra.api.receivable.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="payments", uniqueConstraints=@UniqueConstraint(name="uk_payment_tenant_external",columnNames={"tenant_id","external_id"}))
public class Payment extends AuditableEntity {
 @Id private UUID id; @Column(name="tenant_id",nullable=false) private UUID tenantId; @Column(name="customer_id",nullable=false) private UUID customerId; @Column(name="external_id",nullable=false,length=120) private String externalId; @Column(name="payment_date",nullable=false) private LocalDate paymentDate; @Column(nullable=false,precision=19,scale=4) private BigDecimal amount; @Column(nullable=false,length=3) private String currency; @Column(name="payment_reference",length=200) private String paymentReference; @Column(length=80) private String source; @JdbcTypeCode(SqlTypes.JSON) @Column(name="custom_fields",columnDefinition="jsonb") private JsonNode customFields;
 protected Payment() {}
 public Payment(UUID tenantId,UUID customerId,String externalId,LocalDate paymentDate,BigDecimal amount,String currency,String reference,String source,JsonNode customFields){this.id=UUID.randomUUID();this.tenantId=Objects.requireNonNull(tenantId);this.customerId=Objects.requireNonNull(customerId);this.externalId=req(externalId);this.paymentDate=Objects.requireNonNull(paymentDate);if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("amount must be positive");this.amount=amount;this.currency=req(currency).toUpperCase(Locale.ROOT);this.paymentReference=trim(reference);this.source=trim(source);this.customFields=customFields==null?null:customFields.deepCopy();}
 public UUID getId(){return id;} public UUID getTenantId(){return tenantId;} public UUID getCustomerId(){return customerId;} public String getExternalId(){return externalId;} public LocalDate getPaymentDate(){return paymentDate;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;} public String getPaymentReference(){return paymentReference;} public String getSource(){return source;} public JsonNode getCustomFields(){return customFields==null?null:customFields.deepCopy();}
 private static String req(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("value is required");return v.trim();} private static String trim(String v){return v==null||v.isBlank()?null:v.trim();}
}
