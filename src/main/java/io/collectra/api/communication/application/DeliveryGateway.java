package io.collectra.api.communication.application;

public interface DeliveryGateway {
    DeliveryResult deliver(DeliveryCommand command);
}
