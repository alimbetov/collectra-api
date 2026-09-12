package io.collectra.api.communication.application;

import org.springframework.stereotype.Component;

@Component
public class NoopDeliveryFaultInjector implements DeliveryFaultInjector {
    @Override
    public void hit(DeliveryFaultPoint point) {
        // Production fail-safe: fault injection is a no-op. Tests may replace this bean explicitly.
    }
}
