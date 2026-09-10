package io.collectra.api.template.application;

public sealed interface TemplateToken
        permits TemplateToken.Text,
                TemplateToken.Placeholder,
                TemplateToken.EachStart,
                TemplateToken.EachEnd {

    record Text(String value) implements TemplateToken {}

    record Placeholder(FieldPath path) implements TemplateToken {}

    record EachStart(String collectionKey) implements TemplateToken {}

    record EachEnd() implements TemplateToken {}
}
