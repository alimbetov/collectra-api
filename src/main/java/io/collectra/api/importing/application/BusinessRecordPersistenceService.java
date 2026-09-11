package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessRecordPersistenceService {
    private final CustomerService customers;
    private final ReceivableService receivables;

    public BusinessRecordPersistenceService(
            CustomerService customers, ReceivableService receivables) {
        this.customers = customers;
        this.receivables = receivables;
    }

    @Transactional
    public PersistResult persist(UUID tenantId, String documentType, ObjectNode payload) {
        return switch (documentType.toUpperCase(Locale.ROOT)) {
            case "CUSTOMER" -> persistCustomer(tenantId, payload);
            case "INVOICE" -> persistInvoice(tenantId, payload);
            case "PAYMENT" -> persistPayment(tenantId, payload);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported business documentType: " + documentType);
        };
    }

    private PersistResult persistCustomer(UUID tenantId, ObjectNode payload) {
        String externalId = requiredText(payload, "/customer/externalId");
        var existing = customers.findByExternalId(tenantId, externalId);
        if (existing.isPresent()) {
            return new PersistResult("CUSTOMER", existing.get().getId(), externalId, false);
        }
        Customer created =
                customers.create(
                        tenantId,
                        externalId,
                        customerType(payload.path("customer").path("type").asText(null)),
                        text(payload, "/customer/displayName", externalId),
                        text(payload, "/customer/firstName", null),
                        text(payload, "/customer/lastName", null),
                        text(payload, "/customer/middleName", null),
                        text(payload, "/customer/companyName", null),
                        null,
                        text(payload, "/customer/locale", null),
                        text(payload, "/customer/timezone", null),
                        object(payload.at("/custom/customer")));
        return new PersistResult("CUSTOMER", created.getId(), externalId, true);
    }

    private PersistResult persistInvoice(UUID tenantId, ObjectNode payload) {
        String externalId = requiredText(payload, "/invoice/externalId");
        var existing = receivables.findInvoiceByExternalId(tenantId, externalId);
        if (existing.isPresent()) {
            return new PersistResult("INVOICE", existing.get().getId(), externalId, false);
        }
        Customer customer = resolveCustomer(tenantId, payload);
        Invoice created =
                receivables.createInvoice(
                        tenantId,
                        customer.getId(),
                        uuid(payload, "/invoice/contractId"),
                        externalId,
                        text(payload, "/invoice/invoiceNumber", externalId),
                        date(payload, "/invoice/invoiceDate", null),
                        requiredDate(payload, "/invoice/dueDate"),
                        requiredDecimal(payload, "/invoice/amount"),
                        requiredText(payload, "/invoice/currency"),
                        uuid(payload, "/invoice/documentFileId"),
                        object(payload.at("/custom/invoice")));
        return new PersistResult("INVOICE", created.getId(), externalId, true);
    }

    private PersistResult persistPayment(UUID tenantId, ObjectNode payload) {
        String externalId = requiredText(payload, "/payment/externalId");
        var existing = receivables.findPaymentByExternalId(tenantId, externalId);
        if (existing.isPresent()) {
            return new PersistResult("PAYMENT", existing.get().getId(), externalId, false);
        }
        Customer customer = resolveCustomer(tenantId, payload);
        Payment created =
                receivables.createPayment(
                        tenantId,
                        customer.getId(),
                        externalId,
                        requiredDate(payload, "/payment/paymentDate"),
                        requiredDecimal(payload, "/payment/amount"),
                        requiredText(payload, "/payment/currency"),
                        text(payload, "/payment/reference", null),
                        text(payload, "/payment/source", null),
                        object(payload.at("/custom/payment")));
        return new PersistResult("PAYMENT", created.getId(), externalId, true);
    }

    private Customer resolveCustomer(UUID tenantId, ObjectNode payload) {
        String externalId = requiredText(payload, "/customer/externalId");
        return customers
                .findByExternalId(tenantId, externalId)
                .orElseGet(
                        () ->
                                customers.create(
                                        tenantId,
                                        externalId,
                                        customerType(
                                                payload.path("customer").path("type").asText(null)),
                                        text(payload, "/customer/displayName", externalId),
                                        text(payload, "/customer/firstName", null),
                                        text(payload, "/customer/lastName", null),
                                        text(payload, "/customer/middleName", null),
                                        text(payload, "/customer/companyName", null),
                                        null,
                                        text(payload, "/customer/locale", null),
                                        text(payload, "/customer/timezone", null),
                                        object(payload.at("/custom/customer"))));
    }

    private CustomerType customerType(String value) {
        return value == null || value.isBlank()
                ? CustomerType.COMPANY
                : CustomerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private String requiredText(ObjectNode payload, String pointer) {
        String value = text(payload, pointer, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required mapped field is missing: " + pointer);
        }
        return value;
    }

    private String text(ObjectNode payload, String pointer, String defaultValue) {
        JsonNode node = payload.at(pointer);
        return node.isMissingNode() || node.isNull() ? defaultValue : node.asText(defaultValue);
    }

    private LocalDate requiredDate(ObjectNode payload, String pointer) {
        LocalDate value = date(payload, pointer, null);
        if (value == null) {
            throw new IllegalArgumentException("Required mapped field is missing: " + pointer);
        }
        return value;
    }

    private LocalDate date(ObjectNode payload, String pointer, LocalDate defaultValue) {
        String value = text(payload, pointer, null);
        return value == null || value.isBlank() ? defaultValue : LocalDate.parse(value);
    }

    private BigDecimal requiredDecimal(ObjectNode payload, String pointer) {
        JsonNode node = payload.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("Required mapped field is missing: " + pointer);
        }
        return node.decimalValue();
    }

    private UUID uuid(ObjectNode payload, String pointer) {
        String value = text(payload, pointer, null);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private JsonNode object(JsonNode node) {
        return node != null && node.isObject() ? node : null;
    }

    public record PersistResult(String type, UUID id, String externalId, boolean created) {}
}
