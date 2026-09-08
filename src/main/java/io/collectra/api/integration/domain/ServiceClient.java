package io.collectra.api.integration.domain;

import io.collectra.api.shared.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "service_clients")
public class ServiceClient extends AuditableEntity {
    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "token_ttl_seconds", nullable = false)
    private int tokenTtlSeconds;

    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "service_client_scopes",
            joinColumns = @JoinColumn(name = "service_client_id"))
    @Column(name = "scope_code")
    private Set<String> scopes = new HashSet<>();

    protected ServiceClient() {}

    public ServiceClient(
            UUID tenantId,
            String clientId,
            String name,
            Set<String> scopes,
            Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.name = name;
        this.scopes = new HashSet<>(scopes);
        this.expiresAt = expiresAt;
        this.status = "ACTIVE";
        this.tokenTtlSeconds = 600;
    }

    public void block() {
        status = "BLOCKED";
        authorizationVersion++;
    }

    public void unblock() {
        status = "ACTIVE";
        authorizationVersion++;
    }

    public void credentialsChanged() {
        authorizationVersion++;
    }

    public boolean activeAt(Instant now) {
        return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean active() {
        return activeAt(Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Set<String> getScopes() {
        return Set.copyOf(scopes);
    }

    public Duration tokenTtl() {
        return Duration.ofSeconds(tokenTtlSeconds);
    }

    public long getAuthorizationVersion() {
        return authorizationVersion;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
