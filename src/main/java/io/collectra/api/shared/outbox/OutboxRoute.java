package io.collectra.api.shared.outbox;

public record OutboxRoute(String exchange, String routingKey) {}
