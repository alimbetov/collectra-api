package io.collectra.api.contract.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "contracts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_contract_tenant_external",
                        columnNames = {"tenant_id", "external_id"}))
public class Contract extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(name = "contract_number", nullable = false, length = 160)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private JsonNode customFields;

    protected Contract() {}

    public Contract(
            UUID tenantId,
            UUID customerId,
            String externalId,
            String contractNumber,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.customerId = Objects.requireNonNull(customerId);
        this.externalId = required(externalId, "externalId");
        this.contractNumber = required(contractNumber, "contractNumber");
        this.status = ContractStatus.ACTIVE;
        setDates(validFrom, validTo, renewalDate);
        this.customFields = copy(customFields);
    }

    public void update(
            String contractNumber,
            LocalDate validFrom,
            LocalDate validTo,
            LocalDate renewalDate,
            JsonNode customFields) {
        this.contractNumber = required(contractNumber, "contractNumber");
        setDates(validFrom, validTo, renewalDate);
        this.customFields = copy(customFields);
    }

    public void suspend() {
        requireStatus(ContractStatus.ACTIVE);
        status = ContractStatus.SUSPENDED;
    }

    public void activate() {
        requireStatus(ContractStatus.SUSPENDED);
        status = ContractStatus.ACTIVE;
    }

    public void close() {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.SUSPENDED) {
            throw new IllegalStateException("Contract cannot be closed from " + status);
        }
        status = ContractStatus.CLOSED;
    }

    public void cancel() {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.SUSPENDED) {
            throw new IllegalStateException("Contract cannot be cancelled from " + status);
        }
        status = ContractStatus.CANCELLED;
    }

    private void requireStatus(ContractStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Contract transition requires " + expected + " but was " + status);
        }
    }

    private void setDates(LocalDate validFrom, LocalDate validTo, LocalDate renewalDate) {
        this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo must not be before validFrom");
        }
        this.validTo = validTo;
        this.renewalDate = renewalDate;
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

    public String getExternalId() {
        return externalId;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public LocalDate getRenewalDate() {
        return renewalDate;
    }

    public JsonNode getCustomFields() {
        return copy(customFields);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static JsonNode copy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }
}
