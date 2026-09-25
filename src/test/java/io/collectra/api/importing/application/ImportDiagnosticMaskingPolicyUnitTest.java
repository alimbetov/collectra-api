package io.collectra.api.importing.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ImportDiagnosticMaskingPolicyUnitTest {
    private final ImportDiagnosticMaskingPolicy policy = new ImportDiagnosticMaskingPolicy();

    @Test
    void masksPiiAndBoundsAllDiagnosticText() {
        String raw = "john.doe@example.com +7 701 123 45 67 990101301234";
        String masked = policy.maskedSourceValue(raw);

        assertThat(masked).doesNotContain("john.doe@example.com");
        assertThat(masked).doesNotContain("701 123 45 67");
        assertThat(masked).doesNotContain("990101301234");
        assertThat(policy.errorCode("bad code ".repeat(30))).hasSizeLessThanOrEqualTo(100);
        assertThat(policy.safeDetail("x".repeat(700))).hasSize(500);
        assertThat(policy.fieldPath("x".repeat(400))).hasSize(300);
    }
}
