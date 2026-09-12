package io.collectra.api.communication.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import org.springframework.stereotype.Component;

@Component
public class DestinationMasker {
    private static final String MASK = "***";

    public String mask(CommunicationChannel channel, String destination) {
        if (destination == null || destination.isBlank()) {
            return null;
        }
        return switch (channel) {
            case EMAIL -> maskEmail(destination.trim());
            default -> MASK;
        };
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return MASK;
        }
        return email.substring(0, 1) + MASK + email.substring(at);
    }
}
