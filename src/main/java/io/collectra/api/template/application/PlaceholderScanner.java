package io.collectra.api.template.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaceholderScanner {
    private static final String ITEMS_COLLECTION = "items";

    private enum State {
        TEXT,
        OPEN_BRACE,
        PLACEHOLDER,
        CLOSE_BRACE
    }

    public List<TemplateToken> scan(String template) {
        if (template == null) throw new IllegalArgumentException("Template HTML is required");

        List<TemplateToken> tokens = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        StringBuilder expression = new StringBuilder();
        State state = State.TEXT;
        int placeholderStart = -1;
        int eachDepth = 0;

        for (int i = 0; i < template.length(); i++) {
            char ch = template.charAt(i);
            switch (state) {
                case TEXT -> {
                    if (ch == '{') state = State.OPEN_BRACE;
                    else text.append(ch);
                }
                case OPEN_BRACE -> {
                    if (ch == '{') {
                        flushText(tokens, text);
                        expression.setLength(0);
                        placeholderStart = i - 1;
                        state = State.PLACEHOLDER;
                    } else {
                        text.append('{').append(ch);
                        state = State.TEXT;
                    }
                }
                case PLACEHOLDER -> {
                    if (ch == '{') {
                        throw new IllegalArgumentException(
                                "Nested or unescaped template expression at position " + i);
                    }
                    if (ch == '}') state = State.CLOSE_BRACE;
                    else expression.append(ch);
                }
                case CLOSE_BRACE -> {
                    if (ch == '}') {
                        String value = expression.toString().trim();
                        if (value.startsWith("#each")) {
                            if (!"#each items".equals(value)) {
                                throw new IllegalArgumentException(
                                        "Only {{#each items}} collection block is supported at position "
                                                + placeholderStart);
                            }
                            if (eachDepth != 0) {
                                throw new IllegalArgumentException(
                                        "Nested each blocks are not supported at position " + placeholderStart);
                            }
                            tokens.add(new TemplateToken.EachStart(ITEMS_COLLECTION));
                            eachDepth++;
                        } else if ("/each".equals(value)) {
                            if (eachDepth == 0) {
                                throw new IllegalArgumentException(
                                        "Unexpected {{/each}} at position " + placeholderStart);
                            }
                            tokens.add(new TemplateToken.EachEnd());
                            eachDepth--;
                        } else {
                            try {
                                tokens.add(
                                        new TemplateToken.Placeholder(
                                                PlaceholderGrammar.parse(expression.toString())));
                            } catch (IllegalArgumentException ex) {
                                throw new IllegalArgumentException(
                                        ex.getMessage() + " at position " + placeholderStart, ex);
                            }
                        }
                        state = State.TEXT;
                    } else {
                        expression.append('}').append(ch);
                        state = State.PLACEHOLDER;
                    }
                }
            }
        }

        switch (state) {
            case TEXT -> flushText(tokens, text);
            case OPEN_BRACE -> {
                text.append('{');
                flushText(tokens, text);
            }
            case PLACEHOLDER, CLOSE_BRACE ->
                    throw new IllegalArgumentException(
                            "Unclosed placeholder starting at position " + placeholderStart);
        }
        if (eachDepth != 0) throw new IllegalArgumentException("Unclosed {{#each items}} block");
        return List.copyOf(tokens);
    }

    private void flushText(List<TemplateToken> tokens, StringBuilder text) {
        if (text.length() == 0) return;
        tokens.add(new TemplateToken.Text(text.toString()));
        text.setLength(0);
    }
}
