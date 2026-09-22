package io.collectra.api.campaign.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerChannelAddress;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecipientDestinationResolver {
    private final CustomerService customers;

    public RecipientDestinationResolver(CustomerService customers) {
        this.customers = customers;
    }

    public Map<UUID, String> resolvePrimary(
            UUID tenantId, CommunicationChannel channel, Collection<UUID> customerIds) {
        return switch (channel) {
            case EMAIL -> emailPrimary(tenantId, customerIds);
            case SMS, WHATSAPP -> phonePrimary(tenantId, customerIds);
            case TELEGRAM -> channelPrimary(tenantId, customerIds, channel);
            case IN_APP ->
                    throw new IllegalArgumentException(
                            "IN_APP campaign destination is not supported yet");
        };
    }

    public Map<UUID, Set<String>> activeDestinations(
            UUID tenantId, CommunicationChannel channel, Collection<UUID> customerIds) {
        return switch (channel) {
            case EMAIL -> activeEmails(tenantId, customerIds);
            case SMS, WHATSAPP -> activePhones(tenantId, customerIds);
            case TELEGRAM -> activeChannelAddresses(tenantId, customerIds, channel);
            case IN_APP -> Map.of();
        };
    }

    private Map<UUID, String> emailPrimary(UUID tenantId, Collection<UUID> customerIds) {
        Map<UUID, DestinationCandidate> result = new LinkedHashMap<>();
        for (CustomerEmail email : customers.emailsByCustomerIds(tenantId, customerIds)) {
            if ("ACTIVE".equals(email.getStatus())) {
                putCandidate(result, email.getCustomerId(), email.getEmail(), email.isPrimary());
            }
        }
        return values(result);
    }

    private Map<UUID, String> phonePrimary(UUID tenantId, Collection<UUID> customerIds) {
        Map<UUID, DestinationCandidate> result = new LinkedHashMap<>();
        for (CustomerPhone phone : customers.phonesByCustomerIds(tenantId, customerIds)) {
            if ("ACTIVE".equals(phone.getStatus())) {
                putCandidate(
                        result,
                        phone.getCustomerId(),
                        phone.getNormalizedPhone(),
                        phone.isPrimary());
            }
        }
        return values(result);
    }

    private Map<UUID, String> channelPrimary(
            UUID tenantId, Collection<UUID> customerIds, CommunicationChannel channel) {
        Map<UUID, DestinationCandidate> result = new LinkedHashMap<>();
        for (CustomerChannelAddress address :
                customers.channelAddressesByCustomerIds(tenantId, customerIds, channel.name())) {
            if (address.isActive()) {
                putCandidate(
                        result, address.getCustomerId(), address.getAddress(), address.isPrimary());
            }
        }
        return values(result);
    }

    private Map<UUID, Set<String>> activeEmails(UUID tenantId, Collection<UUID> customerIds) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        for (CustomerEmail email : customers.emailsByCustomerIds(tenantId, customerIds)) {
            if ("ACTIVE".equals(email.getStatus())) {
                add(result, email.getCustomerId(), email.getEmail());
            }
        }
        return immutable(result);
    }

    private Map<UUID, Set<String>> activePhones(UUID tenantId, Collection<UUID> customerIds) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        for (CustomerPhone phone : customers.phonesByCustomerIds(tenantId, customerIds)) {
            if ("ACTIVE".equals(phone.getStatus())) {
                add(result, phone.getCustomerId(), phone.getNormalizedPhone());
            }
        }
        return immutable(result);
    }

    private Map<UUID, Set<String>> activeChannelAddresses(
            UUID tenantId, Collection<UUID> customerIds, CommunicationChannel channel) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        for (CustomerChannelAddress address :
                customers.channelAddressesByCustomerIds(tenantId, customerIds, channel.name())) {
            if (address.isActive()) {
                add(result, address.getCustomerId(), address.getAddress());
            }
        }
        return immutable(result);
    }

    private static void putCandidate(
            Map<UUID, DestinationCandidate> result,
            UUID customerId,
            String destination,
            boolean primary) {
        if (destination == null || destination.isBlank()) {
            return;
        }
        DestinationCandidate current = result.get(customerId);
        if (current == null || primary && !current.primary()) {
            result.put(customerId, new DestinationCandidate(destination, primary));
        }
    }

    private static void add(Map<UUID, Set<String>> result, UUID customerId, String destination) {
        if (destination != null && !destination.isBlank()) {
            result.computeIfAbsent(customerId, ignored -> new LinkedHashSet<>()).add(destination);
        }
    }

    private static Map<UUID, String> values(Map<UUID, DestinationCandidate> values) {
        Map<UUID, String> result = new LinkedHashMap<>();
        values.forEach((customerId, candidate) -> result.put(customerId, candidate.value()));
        return Map.copyOf(result);
    }

    private static Map<UUID, Set<String>> immutable(Map<UUID, Set<String>> values) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        values.forEach(
                (customerId, destinations) -> result.put(customerId, Set.copyOf(destinations)));
        return Map.copyOf(result);
    }

    private record DestinationCandidate(String value, boolean primary) {}
}
