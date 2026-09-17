package io.collectra.api.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.collectra.api.shared.error.InvalidRequestException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalStringTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void roundTripsCanonicalPlainStringsWithoutPrecisionLoss() throws Exception {
        DecimalString boundary = DecimalString.parse("999999999999999.9999");

        assertThat(boundary.value()).isEqualByComparingTo(new BigDecimal("999999999999999.9999"));
        assertThat(json.writeValueAsString(boundary)).isEqualTo("\"999999999999999.9999\"");
        assertThat(json.readValue("\"999999999999999.9999\"", DecimalString.class))
                .isEqualTo(boundary);
    }

    @Test
    void normalizesTrailingAndNegativeZeroWithoutExponent() throws Exception {
        assertThat(DecimalString.parse("150000.2500").canonical()).isEqualTo("150000.25");
        assertThat(DecimalString.parse("-0.0000").canonical()).isEqualTo("0");
        assertThat(DecimalString.of(new BigDecimal("100000000000000")).canonical())
                .isEqualTo("100000000000000");
        assertThat(json.writeValueAsString(DecimalString.parse("0.0001"))).isEqualTo("\"0.0001\"");
    }

    @Test
    void rejectsNonStringAndNonCanonicalLexicalForms() {
        assertThatThrownBy(() -> json.readValue("1.25", DecimalString.class))
                .hasRootCauseInstanceOf(InvalidRequestException.class);
        assertInvalid("01", "INVALID_DECIMAL");
        assertInvalid("1e3", "INVALID_DECIMAL");
        assertInvalid("+1", "INVALID_DECIMAL");
        assertInvalid(" 1 ", "INVALID_DECIMAL");
        assertInvalid("1,25", "INVALID_DECIMAL");
    }

    @Test
    void rejectsScalePrecisionAndIntegerRangeOverflow() {
        assertInvalid("0.00001", "INVALID_DECIMAL");
        assertInvalid("1000000000000000", "DECIMAL_OUT_OF_RANGE");
        assertInvalid("1000000000000000.0000", "DECIMAL_OUT_OF_RANGE");
    }

    private void assertInvalid(String value, String code) {
        assertThatThrownBy(() -> DecimalString.parse(value))
                .isInstanceOfSatisfying(
                        InvalidRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
