package io.collectra.api.integration.application;

import io.collectra.api.identity.application.JwtService;
import io.collectra.api.integration.domain.ServiceClient;
import io.collectra.api.integration.domain.ServiceClientCredential;
import io.collectra.api.integration.infrastructure.ServiceClientCredentialRepository;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import io.collectra.api.shared.security.InMemoryRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceClientService {
    private static final Set<String> ALLOWED_SCOPES =
            Set.of(
                    "integration:notifications:send",
                    "integration:otp:create",
                    "integration:imports:create",
                    "integration:imports:read",
                    "document:generate",
                    "document:read",
                    "file:upload",
                    "file:read",
                    "file:delete");

    private final ServiceClientRepository clients;
    private final ServiceClientCredentialRepository credentials;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final InMemoryRateLimiter limiter;

    public ServiceClientService(
            ServiceClientRepository clients,
            ServiceClientCredentialRepository credentials,
            PasswordEncoder passwords,
            JwtService jwt,
            InMemoryRateLimiter limiter) {
        this.clients = clients;
        this.credentials = credentials;
        this.passwords = passwords;
        this.jwt = jwt;
        this.limiter = limiter;
    }

    @Transactional
    public ClientResponse create(
            UUID tenantId,
            String clientId,
            String name,
            String clientSecret,
            Set<String> scopes,
            Instant clientExpiresAt,
            Instant secretExpiresAt) {
        validateSecret(clientSecret);
        validateScopes(scopes);
        validateFuture(clientExpiresAt, "Client expiration");
        validateFuture(secretExpiresAt, "Secret expiration");
        if (clients.existsByClientId(clientId)) {
            throw new IllegalArgumentException("Client id already exists");
        }

        ServiceClient client =
                clients.save(new ServiceClient(tenantId, clientId, name, scopes, clientExpiresAt));
        ServiceClientCredential credential =
                credentials.save(
                        new ServiceClientCredential(
                                client.getId(),
                                passwords.encode(clientSecret),
                                secretHint(clientSecret),
                                "ACTIVE",
                                secretExpiresAt));
        return response(client, credential);
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list(UUID tenantId) {
        return clients.findAllByTenantId(tenantId).stream()
                .map(client -> response(client, newestUsableCredential(client.getId())))
                .toList();
    }

    @Transactional
    public ClientResponse startRotation(
            UUID tenantId, UUID id, String clientSecret, Instant secretExpiresAt) {
        validateSecret(clientSecret);
        validateFuture(secretExpiresAt, "Secret expiration");
        ServiceClient client = requireClient(tenantId, id);
        Instant now = Instant.now();
        List<ServiceClientCredential> existing = credentials.findAllByServiceClientId(id);
        existing.stream()
                .filter(credential -> "ROTATING".equals(credential.getStatus()))
                .forEach(credential -> credential.revoke(now));
        ServiceClientCredential next =
                credentials.save(
                        new ServiceClientCredential(
                                id,
                                passwords.encode(clientSecret),
                                secretHint(clientSecret),
                                "ROTATING",
                                secretExpiresAt));
        return response(client, next);
    }

    @Transactional
    public ClientResponse completeRotation(UUID tenantId, UUID id, UUID credentialId) {
        ServiceClient client = requireClient(tenantId, id);
        ServiceClientCredential selected =
                credentials
                        .findById(credentialId)
                        .filter(credential -> credential.getServiceClientId().equals(id))
                        .orElseThrow();
        if (!"ROTATING".equals(selected.getStatus())) {
            throw new IllegalArgumentException("Credential is not awaiting rotation");
        }
        Instant now = Instant.now();
        credentials.findAllByServiceClientId(id).stream()
                .filter(credential -> !credential.getId().equals(credentialId))
                .filter(credential -> credential.usableAt(now))
                .forEach(credential -> credential.revoke(now));
        selected.activate();
        client.credentialsChanged();
        return response(client, selected);
    }

    @Transactional
    public void block(UUID tenantId, UUID id) {
        requireClient(tenantId, id).block();
    }

    @Transactional
    public void unblock(UUID tenantId, UUID id) {
        requireClient(tenantId, id).unblock();
    }

    @Transactional
    public TokenResponse token(String clientId, String secret, Set<String> requestedScopes) {
        limiter.check("service:" + clientId, 30, Duration.ofMinutes(1));
        validateScopes(requestedScopes);
        Instant now = Instant.now();
        ServiceClient client =
                clients.findByClientId(clientId)
                        .filter(value -> value.activeAt(now))
                        .orElseThrow(() -> new BadCredentialsException("Invalid service client"));
        if (!client.getScopes().containsAll(requestedScopes)) {
            throw new BadCredentialsException("Invalid service client");
        }
        ServiceClientCredential credential =
                credentials.findAllByServiceClientId(client.getId()).stream()
                        .filter(value -> value.usableAt(now))
                        .filter(value -> passwords.matches(secret, value.getSecretHash()))
                        .findFirst()
                        .orElseThrow(() -> new BadCredentialsException("Invalid service client"));
        credential.usedAt(now);
        String token =
                jwt.issueService(
                        client.getId(),
                        client.getTenantId(),
                        client.getClientId(),
                        List.copyOf(requestedScopes),
                        client.tokenTtl(),
                        client.getAuthorizationVersion());
        return new TokenResponse(token, "Bearer", client.tokenTtl().toSeconds());
    }

    private ServiceClient requireClient(UUID tenantId, UUID id) {
        return clients.findByIdAndTenantId(id, tenantId).orElseThrow();
    }

    private ServiceClientCredential newestUsableCredential(UUID clientId) {
        Instant now = Instant.now();
        return credentials.findAllByServiceClientId(clientId).stream()
                .filter(value -> value.usableAt(now))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private ClientResponse response(
            ServiceClient client, ServiceClientCredential credential) {
        return new ClientResponse(
                client.getId(),
                client.getClientId(),
                client.getName(),
                client.getStatus(),
                client.getScopes(),
                client.getExpiresAt(),
                credential == null ? null : credential.getId(),
                credential == null ? null : credential.getSecretHint(),
                credential == null ? null : credential.getStatus(),
                credential == null ? null : credential.getExpiresAt(),
                credential == null ? null : credential.getLastUsedAt());
    }

    private void validateSecret(String secret) {
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 32 || bytes > 72) {
            throw new IllegalArgumentException(
                    "Client secret must contain 32 to 72 UTF-8 bytes");
        }
    }

    private void validateScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty() || !ALLOWED_SCOPES.containsAll(scopes)) {
            throw new IllegalArgumentException("Unknown or empty service scope");
        }
    }

    private void validateFuture(Instant value, String field) {
        if (value != null && !value.isAfter(Instant.now())) {
            throw new IllegalArgumentException(field + " must be in the future");
        }
    }

    private String secretHint(String secret) {
        int from = Math.max(0, secret.length() - 4);
        return "****" + secret.substring(from);
    }

    public record ClientResponse(
            UUID id,
            String clientId,
            String name,
            String status,
            Set<String> scopes,
            Instant expiresAt,
            UUID credentialId,
            String secretHint,
            String credentialStatus,
            Instant secretExpiresAt,
            Instant lastUsedAt) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
