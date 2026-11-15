package io.collectra.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Narrow same-origin BFF gateway. Collectra API remains the final authority for
 * tenant membership, roles, account status and verified identity links.
 */
@RestController
@ConditionalOnProperty(name = "app-common.bff.collectra-proxy-enabled", havingValue = "true")
public class IamCollectraProxyController {
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> REQUEST_HEADERS = Set.of("content-type", "accept", "if-match", "if-none-match");
    private static final Set<String> RESPONSE_HEADERS = Set.of("content-type", "etag", "cache-control");
    private final OAuth2AuthorizedClientManager clients;
    private final RestClient http;
    private final URI target;

    public IamCollectraProxyController(OAuth2AuthorizedClientManager clients,
            @Value("${app-common.collectra-api.base-url:http://localhost:8080}") String baseUrl) {
        this.clients = clients;
        target = URI.create(baseUrl);
        if (!("https".equalsIgnoreCase(target.getScheme()) ||
                ("http".equalsIgnoreCase(target.getScheme()) && "localhost".equalsIgnoreCase(target.getHost())))
                || target.getRawQuery() != null || target.getRawFragment() != null
                || target.getUserInfo() != null)
            throw new IllegalArgumentException("Collectra upstream must be a trusted HTTPS URL");
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setInstanceFollowRedirects(false);
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        http = RestClient.builder().requestFactory(factory).build();
    }

    @RequestMapping("/api/v1/iam/collectra/**")
    public ResponseEntity<byte[]> forward(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Authentication authentication,
            @org.springframework.web.bind.annotation.RequestBody(required = false) byte[] body) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                || !(oauth.getPrincipal() instanceof OidcUser))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!METHODS.contains(request.getMethod())) return ResponseEntity.status(405).build();
        String prefix = "/api/v1/iam/collectra";
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(prefix + "/api/v1/")) return ResponseEntity.badRequest().build();
        String suffix = path.substring(prefix.length());
        if (suffix.contains("..") || suffix.contains("%") || suffix.contains("\\"))
            return ResponseEntity.badRequest().build();
        // Credential and IAM administration endpoints are not exposed through the BFF.
        if (suffix.startsWith("/api/v1/auth/") || suffix.startsWith("/api/v1/platform/auth/")
                || suffix.startsWith("/api/v1/platform/iam/") || suffix.startsWith("/api/v1/integration/service-token"))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (body != null && body.length > 1024 * 1024) return ResponseEntity.status(413).build();
        var authorize = OAuth2AuthorizeRequest.withClientRegistrationId(oauth.getAuthorizedClientRegistrationId())
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(jakarta.servlet.http.HttpServletResponse.class.getName(), response).build();
        var client = clients.authorize(authorize);
        if (client == null || client.getAccessToken() == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String tenant = request.getHeader("X-Collectra-Tenant-ID");
        if (tenant != null) {
            try { java.util.UUID.fromString(tenant); }
            catch (IllegalArgumentException ex) { return ResponseEntity.badRequest().build(); }
        }
        String query = request.getQueryString();
        if (query != null && (query.contains("#") || query.contains("\r") || query.contains("\n")))
            return ResponseEntity.badRequest().build();
        URI upstream = URI.create(target.toString().replaceAll("/+$", "") + suffix
                + (query == null ? "" : "?" + query));
        try {
            return http.method(HttpMethod.valueOf(request.getMethod())).uri(upstream)
                    .headers(headers -> {
                        headers.setBearerAuth(client.getAccessToken().getTokenValue());
                        if (tenant != null) headers.set("X-Collectra-Tenant-ID", tenant);
                        for (String name : REQUEST_HEADERS) {
                            String value = request.getHeader(name);
                            if (value != null) headers.set(name, value);
                        }
                    })
                    .body(body == null ? new byte[0] : body)
                    .exchange((req, res) -> {
                        var headers = new HttpHeaders();
                        for (String name : RESPONSE_HEADERS) {
                            List<String> values = res.getHeaders().get(name);
                            if (values != null) headers.put(name, values);
                        }
                        headers.setCacheControl("no-store");
                        byte[] responseBody = res.getBody().readNBytes(2 * 1024 * 1024 + 1);
                        if (responseBody.length > 2 * 1024 * 1024)
                            return new ResponseEntity<byte[]>(new byte[0], headers, HttpStatus.BAD_GATEWAY);
                        return new ResponseEntity<>(responseBody, headers, res.getStatusCode());
                    });
        } catch (org.springframework.web.client.RestClientException | java.io.UncheckedIOException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
