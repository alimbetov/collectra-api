package io.collectra.api.campaign.application;

import io.collectra.api.communication.domain.CommunicationChannel;
import io.collectra.api.customer.application.CustomerService;
import io.collectra.api.customer.domain.CustomerChannelAddress;
import io.collectra.api.customer.domain.CustomerEmail;
import io.collectra.api.customer.domain.CustomerPhone;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
            case EMAIL -> primary(
                    customers.emailsByCustomerIds(tenantId, customerIds),
                    CustomerEmail::getCustomerId,
                    CustomerEmail::isPrimary,
                    value -> "ACTIVE".equals(value.getStatus()),
                    CustomerEmail::getEmail);
            case SMS, WHATSAPP -> primary(
                    customers.phonesByCustomerIds(tenantId, customerIds),
                    CustomerPhone::getCustomerId,
                    CustomerPhone::isPrimary,
                    value -> "ACTIVE".equals(value.getStatus()),
                    CustomerPhone::getNormalizedPhone);
            case TELEGRAM -> primary(
                    customers.channelAddressesByCustomerIds(
                            tenantId, customerIds, CommunicationChannel.TELEGRAM.name()),
                    CustomerChannelAddress::getCustomerId,
                    CustomerChannelAddress::isPrimary,
                    CustomerChannelAddress::isActive,
                    CustomerChannelAddress::getAddress);
            case IN_APP -> throw new IllegalArgumentException(
                    "IN_APP campaign destination is not supported yet");
        };
    }

    public Map<UUID, Set<String>> activeDestinations(
            UUID tenantId, CommunicationChannel channel, Collection<UUID> customerIds) {
        return switch (channel) {
            case EMAIL -> active(
                    customers.emailsByCustomerIds(tenantId, customerIds),
                    CustomerEmail::getCustomerId,
                    value -> "ACTIVE".equals(value.getStatus()),
                    CustomerEmail::getEmail);
            case SMS, WHATSAPP -> active(
                    customers.phonesByCustomerIds(tenantId, customerIds),
                    CustomerPhone::getCustomerId,
                    value -> "ACTIVE".equals(value.getStatus()),
                    CustomerPhone::getNormalizedPhone);
            case TELEGRAM -> active(
                    customers.channelAddressesByCustomerIds(
                            tenantId, customerIds, CommunicationChannel.TELEGRAM.name()),
                    CustomerChannelAddress::getCustomerId,
                    CustomerChannelAddress::isActive,
                    CustomerChannelAddress::getAddress);
            case IN_APP -> Map.of();
        };
    }

    private static <T> Map<UUID, String> primary(
            List<T> values,
            Function<T, UUID> customerId,
            java.util.function.Predicate<T> primary,
            java.util.function.Predicate<T> active,
            Function<T, String> destination) {
        Map<UUID, List<T>> grouped =
                values.stream()
                        .filter(active)
                        .collect(Collectors.groupingBy(customerId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, String> result = new LinkedHashMap<>();
        grouped.forEach(
                (id, contacts) ->
                        contacts.stream()
                                .sorted(
                                        Comparator.comparing(
                                                        (T value) -> primary.test(value))
                                                .reversed())
                                .map(destination)
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst()
                                .ifPresent(value -> result.put(id, value)));
        return Map.copyOf(result);
    }

    private static <T> Map<UUID, Set<String>> active(
            List<T> values,
            Function<T, UUID> customerId,
            java.util.function.Predicate<T> active,
            Function<T, String> destination) {
        Map<UUID, Set<String>> result = new LinkedHashMap<>();
        values.stream()
                .filter(active)
                .forEach(
                        value -> {
                            String resolved = destination.apply(value);
                            if (resolved != null && !resolved.isBlank()) {
                                result.computeIfAbsent(
                                                customerId.apply(value),
                                                ignored -> new LinkedHashSet<>())
                                        .add(resolved);
                            }
                        });
        return result.entrySet().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
