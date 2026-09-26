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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        assertThat(receivableService.allocations(tenantId, payment.getId(), 0, 50).getContent())
                .hasSize(1);
    }
    @Test
    void allocationReplayIsIdempotentAndConflictingIntentIsRejected() {
        Fixture f = fixture("100.00", "100.00");
        UUID commandId = UUID.randomUUID();

        var first = receivableService.allocate(f.tenantId(), f.payment().getId(), commandId, f.invoice().getId(), new BigDecimal("60.00"));
        var replay = receivableService.allocate(f.tenantId(), f.payment().getId(), commandId, f.invoice().getId(), new BigDecimal("60.00"));

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(receivableService.allocations(f.tenantId(), f.payment().getId(), 0, 50).getTotalElements()).isEqualTo(1);
        assertThat(receivableService.invoice(f.tenantId(), f.invoice().getId()).getOutstandingAmount()).isEqualByComparingTo("40.00");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                receivableService.allocate(f.tenantId(), f.payment().getId(), commandId, f.invoice().getId(), new BigDecimal("50.00")))
                .hasMessageContaining("commandId");
        assertThat(receivableService.invoice(f.tenantId(), f.invoice().getId()).getOutstandingAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void concurrentAllocationsCannotOverAllocatePaymentOrInvoice() throws Exception {
        Fixture f = fixture("100.00", "100.00");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> task = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                try {
                    receivableService.allocate(
                            f.tenantId(), f.payment().getId(), UUID.randomUUID(), f.invoice().getId(), new BigDecimal("70.00"));
                    return true;
                } catch (RuntimeException expectedConflict) {
                    return false;
                }
            };
            Future<Boolean> one = executor.submit(task);
            Future<Boolean> two = executor.submit(task);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(java.util.List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        Invoice invoice = receivableService.invoice(f.tenantId(), f.invoice().getId());
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("70.00");
        assertThat(invoice.getOutstandingAmount()).isEqualByComparingTo("30.00");
        assertThat(receivableService.allocations(f.tenantId(), f.payment().getId(), 0, 50).getTotalElements()).isEqualTo(1);
    }

    private Fixture fixture(String invoiceAmount, String paymentAmount) {
        UUID tenantId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        Customer customer = customerService.create(
                tenantId, "C-" + suffix, CustomerType.COMPANY, "Finance Test", null, null, null,
                "Finance Test", null, "ru-KZ", "Asia/Almaty", null);
        Invoice invoice = receivableService.createInvoice(
                tenantId, customer.getId(), null, "INV-" + suffix, "INV-" + suffix,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10), new BigDecimal(invoiceAmount),
                "KZT", null, null);
        Payment payment = receivableService.createPayment(
                tenantId, customer.getId(), "PAY-" + suffix, LocalDate.of(2026, 9, 11),
                new BigDecimal(paymentAmount), "KZT", "REF-" + suffix, "TEST", null);
        return new Fixture(tenantId, invoice, payment);
    }

    private record Fixture(UUID tenantId, Invoice invoice, Payment payment) {}

}
