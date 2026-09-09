package io.collectra.api.template.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PlaceholderGrammar {
    private static final Pattern KEY =
            Pattern.compile("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+");

    private PlaceholderGrammar() {}

    public static FieldPath parse(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("Placeholder expression is required");
        }
        String key = expression.trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Placeholder expression is empty");
        }
        if (!key.equals(key.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Placeholder keys must use canonical lowercase form: " + key);
        }
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid placeholder expression: " + key);
        }
        return new FieldPath(Arrays.asList(key.split("\\.")));
    }
}
