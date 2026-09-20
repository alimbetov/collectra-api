package io.collectra.api.contract.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractTest {
    @Test
    void lifecycleAllowsOnlyDocumentedTransitions() {
        Contract contract = contract();

        contract.suspend();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SUSPENDED);
        contract.activate();
        contract.close();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.CLOSED);
        assertThatThrownBy(contract::activate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(contract::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAnInvertedValidityRange() {
        assertThatThrownBy(
                        () ->
                                new Contract(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "EXT",
                                        "CN",
                                        LocalDate.parse("2026-05-01"),
                                        LocalDate.parse("2026-04-01"),
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validTo");
    }

    private Contract contract() {
        return new Contract(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "EXT",
                "CN",
                LocalDate.parse("2026-01-01"),
                null,
                null,
                null);
    }
}
