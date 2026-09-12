package io.collectra.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.Customer;
import io.collectra.api.customer.domain.CustomerType;
import io.collectra.api.receivable.application.ReceivableService;
import io.collectra.api.receivable.domain.Invoice;
import io.collectra.api.receivable.domain.Payment;
import io.collectra.api.receivable.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CustomerReceivableCoreIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CustomerService customerService;
    @Autowired private ReceivableService receivableService;

    @Test
    void shouldPersistCustomerInvoicePaymentAndAllocation() {
        UUID tenantId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();

        Customer customer =
                customerService.create(
                        tenantId,
                        "C-" + suffix,
                        CustomerType.COMPANY,
                        "Test Company",
                        null,
                        null,
                        null,
                        "Test Company",
                        null,
                        "ru-KZ",
                        "Asia/Almaty",
                        null);

        Invoice invoice =
                receivableService.createInvoice(
                        tenantId,
                        customer.getId(),
                        null,
                        "INV-" + suffix,
                        "2026-001",
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 10),
                        new BigDecimal("1000.00"),
                        "KZT",
                        null,
                        null);

        Payment payment =
                receivableService.createPayment(
                        tenantId,
                        customer.getId(),
                        "PAY-" + suffix,
                        LocalDate.of(2026, 9, 11),
                        new BigDecimal("400.00"),
                        "KZT",
                        "BANK-REF-1",
                        "ERP",
                        null);

        receivableService.allocate(
                tenantId,
                payment.getId(),
                UUID.randomUUID(),
                invoice.getId(),
                new BigDecimal("400.00"));

        Invoice persisted = receivableService.invoice(tenantId, invoice.getId());
        assertThat(persisted.getPaidAmount()).isEqualByComparingTo("400.00");
        assertThat(persisted.getOutstandingAmount()).isEqualByComparingTo("600.00");
        assertThat(persisted.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(receivableService.allocations(tenantId, payment.getId())).hasSize(1);
    }
}
