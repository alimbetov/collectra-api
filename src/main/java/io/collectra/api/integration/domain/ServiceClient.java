package io.collectra.api.integration.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import java.time.Duration;
import java.util.*;

@Entity
@Table(name = "service_clients")
public class ServiceClient extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "client_id", nullable = false, unique = true, length = 100) private String clientId;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "secret_hash", nullable = false, length = 100) private String secretHash;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "token_ttl_seconds", nullable = false) private int tokenTtlSeconds;
    @Column(name = "authorization_version", nullable = false) private long authorizationVersion;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_client_scopes", joinColumns = @JoinColumn(name = "service_client_id"))
    @Column(name = "scope_code") private Set<String> scopes = new HashSet<>();

    protected ServiceClient() {}
    public ServiceClient(UUID tenantId, String clientId, String name, String secretHash, Set<String> scopes) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.clientId = clientId;
        this.name = name; this.secretHash = secretHash; this.scopes = new HashSet<>(scopes);
        this.status = "ACTIVE"; this.tokenTtlSeconds = 300;
    }
    public void rotateSecret(String hash) { secretHash = hash; authorizationVersion++; }
    public void block() { status = "BLOCKED"; authorizationVersion++; }
    public boolean active() { return "ACTIVE".equals(status); }
    public UUID getId() { return id; } public UUID getTenantId() { return tenantId; }
    public String getClientId() { return clientId; } public String getName() { return name; }
    public String getSecretHash() { return secretHash; } public Set<String> getScopes() { return Set.copyOf(scopes); }
    public Duration tokenTtl() { return Duration.ofSeconds(tokenTtlSeconds); }
    public long getAuthorizationVersion() { return authorizationVersion; }
}
