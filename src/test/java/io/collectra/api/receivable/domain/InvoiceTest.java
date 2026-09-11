package io.collectra.api.receivable.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    @Test
    void shouldApplyPartialAndFullPayment() {
        Invoice invoice = invoice(new BigDecimal("1000.00"));

        invoice.apply(new BigDecimal("250.00"));

        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("250.00");
        assertThat(invoice.getOutstandingAmount()).isEqualByComparingTo("750.00");
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);

        invoice.apply(new BigDecimal("750.00"));

        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("1000.00");
        assertThat(invoice.getOutstandingAmount()).isZero();
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void shouldRejectAllocationAboveOutstandingAmount() {
        Invoice invoice = invoice(new BigDecimal("1000.00"));

        assertThatThrownBy(() -> invoice.apply(new BigDecimal("1000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Allocation exceeds invoice outstanding amount");
    }

    @Test
    void shouldCalculateOverdueWithoutPersistingOverdueStatus() {
        Invoice invoice = invoice(new BigDecimal("1000.00"));

        assertThat(invoice.isOverdue(LocalDate.of(2026, 9, 11))).isTrue();

        invoice.apply(new BigDecimal("1000.00"));

        assertThat(invoice.isOverdue(LocalDate.of(2026, 9, 11))).isFalse();
    }

    private Invoice invoice(BigDecimal amount) {
        return new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "INV-EXT-1",
                "INV-1",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1),
                amount,
                "KZT",
                null,
                null);
    }
}
