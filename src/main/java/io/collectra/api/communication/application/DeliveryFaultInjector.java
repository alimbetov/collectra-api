package io.collectra.api.communication.application;

@FunctionalInterface
public interface DeliveryFaultInjector {
    void hit(DeliveryFaultPoint point);

    static DeliveryFaultInjector noop() {
        return point -> {};
    }
}
