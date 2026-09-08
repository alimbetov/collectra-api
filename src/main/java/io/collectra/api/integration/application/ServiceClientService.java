package io.collectra.api.integration.application;

import io.collectra.api.identity.application.JwtService;
import io.collectra.api.integration.domain.ServiceClient;
import io.collectra.api.integration.infrastructure.ServiceClientRepository;
import io.collectra.api.shared.security.InMemoryRateLimiter;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceClientService {
    private final ServiceClientRepository clients; private final PasswordEncoder passwords;
    private final JwtService jwt; private final InMemoryRateLimiter limiter; private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    public ServiceClientService(ServiceClientRepository clients, PasswordEncoder passwords, JwtService jwt,
            InMemoryRateLimiter limiter, JdbcTemplate jdbc) {
        this.clients = clients; this.passwords = passwords; this.jwt = jwt; this.limiter = limiter; this.jdbc = jdbc;
    }
    @Transactional
    public SecretResponse create(UUID tenantId, String clientId, String name, Set<String> scopes, Set<String> ipAllowlist) {
        String secret = secret();
        ServiceClient client = clients.save(new ServiceClient(tenantId, clientId, name, passwords.encode(secret), scopes));
        ipAllowlist.forEach(cidr -> jdbc.update(
                "insert into service_client_ip_rules(id, service_client_id, cidr, enabled) values (?, ?, ?, true)",
                UUID.randomUUID(), client.getId(), cidr));
        return new SecretResponse(client.getId(), client.getClientId(), secret);
    }
    @Transactional
    public SecretResponse rotate(UUID tenantId, UUID id) {
        ServiceClient client = clients.findByIdAndTenantId(id, tenantId).orElseThrow(); String secret = secret();
        client.rotateSecret(passwords.encode(secret)); return new SecretResponse(client.getId(), client.getClientId(), secret);
    }
    @Transactional(readOnly = true)
    public TokenResponse token(String clientId, String secret, Set<String> requestedScopes, String sourceIp) {
        limiter.check("service:" + clientId + ":" + sourceIp, 30, Duration.ofMinutes(1));
        ServiceClient client = clients.findByClientId(clientId).filter(ServiceClient::active)
                .orElseThrow(() -> new BadCredentialsException("Invalid service client"));
        if (!passwords.matches(secret, client.getSecretHash()) || !client.getScopes().containsAll(requestedScopes))
            throw new BadCredentialsException("Invalid service client");
        List<String> rules = jdbc.queryForList(
                "select cidr from service_client_ip_rules where service_client_id = ? and enabled", String.class, client.getId());
        if (!rules.isEmpty() && rules.stream().noneMatch(rule -> IpMatcher.matches(sourceIp, rule)))
            throw new BadCredentialsException("Invalid service client");
        String token = jwt.issueService(client.getId(), client.getTenantId(), client.getClientId(),
                List.copyOf(requestedScopes), client.tokenTtl(), client.getAuthorizationVersion());
        return new TokenResponse(token, "Bearer", client.tokenTtl().toSeconds());
    }
    private String secret() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public record SecretResponse(UUID id, String clientId, String clientSecret) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

    static final class IpMatcher {
        static boolean matches(String address, String rule) {
            if (!rule.contains("/")) return rule.equals(address);
            try {
                String[] parts = rule.split("/", 2); int prefix = Integer.parseInt(parts[1]);
                byte[] candidate = java.net.InetAddress.getByName(address).getAddress();
                byte[] network = java.net.InetAddress.getByName(parts[0]).getAddress();
                if (candidate.length != network.length || prefix < 0 || prefix > candidate.length * 8) return false;
                int bytes = prefix / 8, bits = prefix % 8;
                for (int i = 0; i < bytes; i++) if (candidate[i] != network[i]) return false;
                if (bits == 0) return true; int mask = 0xff << (8 - bits);
                return (candidate[bytes] & mask) == (network[bytes] & mask);
            } catch (Exception ignored) { return false; }
        }
    }
}
