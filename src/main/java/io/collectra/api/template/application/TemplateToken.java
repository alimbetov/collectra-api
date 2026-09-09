package io.collectra.api.template.application;

public sealed interface TemplateToken permits TemplateToken.Text, TemplateToken.Placeholder {

    record Text(String value) implements TemplateToken {}

    record Placeholder(FieldPath path) implements TemplateToken {}
}
