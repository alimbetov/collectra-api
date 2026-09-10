package io.collectra.api.file.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.collectra.api.file.domain.FileStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class FileStatusMetricsUnitTest {
    @Test
    void exposesPendingAndDeleteFailedCounts() {
        StoredFileRepository files = mock(StoredFileRepository.class);
        when(files.countByStatus(FileStatus.DELETE_PENDING)).thenReturn(4L);
        when(files.countByStatus(FileStatus.DELETE_FAILED)).thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new FileStatusMetrics(files, registry);

        assertThat(registry.get("collectra.file.cleanup.pending").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("collectra.file.cleanup.delete.failed").gauge().value()).isEqualTo(2.0);
    }
}
