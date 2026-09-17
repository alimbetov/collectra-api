package io.collectra.api.shared.api;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.collectra.api.shared.error.InvalidRequestException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

@Schema(
        type = "string",
        pattern = DecimalString.PATTERN,
        example = "150000.25",
        description =
                "Canonical plain decimal; precision <= 19, scale <= 4, integer digits <= 15; no exponent")
@JsonDeserialize(using = DecimalString.Deserializer.class)
public final class DecimalString {
    public static final String PATTERN = "^-?(0|[1-9][0-9]{0,14})(\\.[0-9]{1,4})?$";
    private static final Pattern LEXICAL =
            Pattern.compile("^-?(0|[1-9][0-9]{0,18})(\\.[0-9]{1,4})?$");
    private static final int MAX_PRECISION = 19;
    private static final int MAX_SCALE = 4;
    private static final int MAX_INTEGER_DIGITS = MAX_PRECISION - MAX_SCALE;

    private final BigDecimal value;
    private final String canonical;

    private DecimalString(BigDecimal value) {
        this.value = value;
        this.canonical = canonical(this.value);
    }

    public static DecimalString parse(String raw) {
        if (raw == null || !LEXICAL.matcher(raw).matches()) {
            throw invalidDecimal();
        }
        try {
            BigDecimal value = new BigDecimal(raw);
            validateInput(value);
            return new DecimalString(value);
        } catch (NumberFormatException ex) {
            throw invalidDecimal();
        }
    }

    public static DecimalString of(BigDecimal value) {
        BigDecimal required = Objects.requireNonNull(value, "value");
        if (!fitsDatabaseRange(required)) {
            throw new IllegalStateException("Public money value exceeds NUMERIC(19,4) range");
        }
        return new DecimalString(required);
    }

    public static BigDecimal parseNullable(String raw) {
        return raw == null ? null : parse(raw).value();
    }

    public BigDecimal value() {
        return value;
    }

    public BigDecimal positiveValue(String field) {
        if (value.signum() <= 0) {
            throw new InvalidRequestException(
                    "INVALID_REQUEST", field + " must be greater than or equal to 0.0001");
        }
        return value;
    }

    @JsonValue
    public String canonical() {
        return canonical;
    }

    @Override
    public String toString() {
        return canonical;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof DecimalString that && value.compareTo(that.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    private static void validateInput(BigDecimal value) {
        if (!fitsDatabaseRange(value)) {
            throw new InvalidRequestException(
                    "DECIMAL_OUT_OF_RANGE",
                    "Decimal value exceeds precision 19, scale 4, or 15 integer digits");
        }
    }

    private static boolean fitsDatabaseRange(BigDecimal value) {
        int scale = Math.max(value.scale(), 0);
        int integerDigits = Math.max(value.precision() - scale, 0);
        return value.precision() <= MAX_PRECISION
                && scale <= MAX_SCALE
                && integerDigits <= MAX_INTEGER_DIGITS;
    }

    private static String canonical(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static InvalidRequestException invalidDecimal() {
        return new InvalidRequestException("INVALID_DECIMAL", "Invalid decimal value");
    }

    public static final class Deserializer extends JsonDeserializer<DecimalString> {
        @Override
        public DecimalString deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                throw invalidDecimal();
            }
            return parse(parser.getText());
        }
    }
}
