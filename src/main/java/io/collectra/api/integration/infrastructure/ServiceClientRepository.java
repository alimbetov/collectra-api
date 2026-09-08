package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.domain.ServiceClient;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceClientRepository extends JpaRepository<ServiceClient, UUID> {
    Optional<ServiceClient> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
    Optional<ServiceClient> findByIdAndTenantId(UUID id, UUID tenantId);
    List<ServiceClient> findAllByTenantId(UUID tenantId);
}
