package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.importing.application.BusinessRecordPersistenceService;
import io.collectra.api.receivable.application.ReceivableService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class BusinessRecordPersistenceIntegrationTest extends AbstractIntegrationTest {
    @Autowired private BusinessRecordPersistenceService persistence;
    @Autowired private CustomerService customers;
    @Autowired private ReceivableService receivables;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void persistsInvoiceAndReusesItOnRetry() {
        UUID tenantId = tenantId();
        ObjectNode payload = json.createObjectNode();
        payload.withObject("customer").put("externalId", "ERP-C-100").put("displayName", "Acme LLP");
        payload.withObject("invoice")
                .put("externalId", "ERP-I-100")
                .put("invoiceNumber", "INV-100")
                .put("invoiceDate", "2026-09-01")
                .put("dueDate", "2026-09-10")
                .put("amount", new BigDecimal("125000.00"))
                .put("currency", "KZT");

        var first = persistence.persist(tenantId, "INVOICE", payload);
        var second = persistence.persist(tenantId, "INVOICE", payload);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(customers.findByExternalId(tenantId, "ERP-C-100")).isPresent();
        assertThat(receivables.findInvoiceByExternalId(tenantId, "ERP-I-100"))
                .get()
                .extracting(value -> value.getOutstandingAmount())
                .isEqualTo(new BigDecimal("125000.0000"));
    }

    @Test
    void persistsPaymentForExistingCustomer() {
        UUID tenantId = tenantId();
        ObjectNode customerPayload = json.createObjectNode();
        customerPayload
                .withObject("customer")
                .put("externalId", "ERP-C-200")
                .put("displayName", "Customer 200");
        persistence.persist(tenantId, "CUSTOMER", customerPayload);

        ObjectNode paymentPayload = json.createObjectNode();
        paymentPayload.withObject("customer").put("externalId", "ERP-C-200");
        paymentPayload
                .withObject("payment")
                .put("externalId", "ERP-P-200")
                .put("paymentDate", "2026-09-11")
                .put("amount", new BigDecimal("50000.00"))
                .put("currency", "KZT")
                .put("reference", "1C-200")
                .put("source", "1C");

        var result = persistence.persist(tenantId, "PAYMENT", paymentPayload);

        assertThat(result.created()).isTrue();
        assertThat(receivables.findPaymentByExternalId(tenantId, "ERP-P-200")).isPresent();
    }

    private UUID tenantId() {
        return jdbcTemplate.queryForObject(
                "select id from tenants order by created_at limit 1", UUID.class);
    }
}
