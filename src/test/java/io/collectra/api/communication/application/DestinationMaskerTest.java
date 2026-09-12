package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.collectra.api.communication.domain.CommunicationChannel;
import org.junit.jupiter.api.Test;

class DestinationMaskerTest {
    private final DestinationMasker masker = new DestinationMasker();

    @Test
    void masksEmailWithoutLeakingLocalPart() {
        assertThat(masker.mask(CommunicationChannel.EMAIL, "ruslan@example.com"))
                .isEqualTo("r***@example.com");
        assertThat(masker.mask(CommunicationChannel.EMAIL, "ab@example.com"))
                .isEqualTo("a***@example.com");
    }

    @Test
    void malformedAndNonEmailDestinationsAreOpaque() {
        assertThat(masker.mask(CommunicationChannel.EMAIL, "invalid")).isEqualTo("***");
        assertThat(masker.mask(CommunicationChannel.SMS, "+77001234567")).isEqualTo("***");
    }
}
