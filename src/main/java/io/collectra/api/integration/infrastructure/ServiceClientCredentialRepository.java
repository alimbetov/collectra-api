package io.collectra.api.integration.infrastructure;

import io.collectra.api.integration.domain.ServiceClientCredential;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceClientCredentialRepository
        extends JpaRepository<ServiceClientCredential, UUID> {
    List<ServiceClientCredential> findAllByServiceClientId(UUID serviceClientId);
}
