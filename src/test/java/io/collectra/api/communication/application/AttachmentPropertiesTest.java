package io.collectra.api.communication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class AttachmentPropertiesTest {
    @Test
    void defaultsMatchSliceSevenContract() {
        AttachmentProperties properties = new AttachmentProperties();

        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(properties.getMaxTotalSize()).isEqualTo(DataSize.ofMegabytes(20));
        assertThat(properties.getMaxCount()).isEqualTo(10);
    }

    @Test
    void acceptsPositiveCustomLimits() {
        AttachmentProperties properties = new AttachmentProperties();

        properties.setMaxFileSize(DataSize.ofMegabytes(5));
        properties.setMaxTotalSize(DataSize.ofMegabytes(12));
        properties.setMaxCount(4);

        assertThat(properties.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(5));
        assertThat(properties.getMaxTotalSize()).isEqualTo(DataSize.ofMegabytes(12));
        assertThat(properties.getMaxCount()).isEqualTo(4);
    }

    @Test
    void rejectsZeroNegativeAndNullLimits() {
        AttachmentProperties properties = new AttachmentProperties();

        assertThatThrownBy(() -> properties.setMaxFileSize(DataSize.ofBytes(0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxTotalSize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxCount(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxCount(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
