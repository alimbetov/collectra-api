package io.collectra.api.customer.infrastructure;

import io.collectra.api.customer.domain.CustomerChannelAddress;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerChannelAddressRepository
        extends JpaRepository<CustomerChannelAddress, UUID> {

    List<CustomerChannelAddress> findAllByTenantIdAndCustomerIdInAndChannel(
            UUID tenantId, Collection<UUID> customerIds, String channel);
}
