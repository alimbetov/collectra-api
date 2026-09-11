package io.collectra.api.customer.domain;

import com.fasterxml.jackson.databind.JsonNode;
import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "customers",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_customer_tenant_external",
                        columnNames = {"tenant_id", "external_id"}))
public class Customer extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @Column(name = "first_name", length = 120)
    private String firstName;

    @Column(name = "last_name", length = 120)
    private String lastName;

    @Column(name = "middle_name", length = 120)
    private String middleName;

    @Column(name = "company_name", length = 300)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Column(name = "manager_user_id")
    private UUID managerUserId;

    @Column(name = "preferred_locale", length = 35)
    private String preferredLocale;

    @Column(length = 60)
    private String timezone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    private JsonNode customFields;

    protected Customer() {}

    public Customer(
            UUID tenantId,
            String externalId,
            CustomerType customerType,
            String displayName,
            String firstName,
            String lastName,
            String middleName,
            String companyName,
            UUID managerUserId,
            String preferredLocale,
            String timezone,
            JsonNode customFields) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.externalId = required(externalId, "externalId");
        this.customerType = customerType == null ? CustomerType.INDIVIDUAL : customerType;
        this.displayName = required(displayName, "displayName");
        this.firstName = trim(firstName);
        this.lastName = trim(lastName);
        this.middleName = trim(middleName);
        this.companyName = trim(companyName);
        this.managerUserId = managerUserId;
        this.preferredLocale = trim(preferredLocale);
        this.timezone = trim(timezone);
        this.customFields = copy(customFields);
        this.status = CustomerStatus.ACTIVE;
    }

    public void update(
            String displayName,
            String firstName,
            String lastName,
            String middleName,
            String companyName,
            UUID managerUserId,
            String preferredLocale,
            String timezone,
            JsonNode customFields) {
        this.displayName = required(displayName, "displayName");
        this.firstName = trim(firstName);
        this.lastName = trim(lastName);
        this.middleName = trim(middleName);
        this.companyName = trim(companyName);
        this.managerUserId = managerUserId;
        this.preferredLocale = trim(preferredLocale);
        this.timezone = trim(timezone);
        this.customFields = copy(customFields);
    }

    public void changeStatus(CustomerStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getExternalId() {
        return externalId;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public UUID getManagerUserId() {
        return managerUserId;
    }

    public String getPreferredLocale() {
        return preferredLocale;
    }

    public String getTimezone() {
        return timezone;
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

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static JsonNode copy(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }
}
