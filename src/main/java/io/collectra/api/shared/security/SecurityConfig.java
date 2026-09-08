package io.collectra.api.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.collectra.api.shared.tenant.TenantContextFilter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http, AuthorizationVersionFilter authorizationVersionFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests -> requests
                                .requestMatchers(
                                        "/api/v1/auth/tenants/register",
                                        "/api/v1/auth/login",
                                        "/api/v1/auth/refresh",
                                        "/api/v1/auth/logout",
                                        "/api/v1/auth/invitations/accept",
                                        "/api/v1/auth/password/forgot",
                                        "/api/v1/auth/password/reset",
                                        "/api/v1/integration/service-token",
                                        "/actuator/health",
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/auth/otp/challenges/*/verify")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(
                                jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .addFilterAfter(authorizationVersionFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new TenantContextFilter(), AuthorizationVersionFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        String id = "bcrypt";
        var encoders = new java.util.HashMap<String, PasswordEncoder>();
        encoders.put(id, new BCryptPasswordEncoder(12));
        DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder(id, encoders);
        encoder.setDefaultPasswordEncoderForMatches(encoders.get(id));
        return encoder;
    }

    @Bean
    SecretKey jwtKey(@Value("${collectra.security.jwt-secret}") String secret) {
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 characters");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuer =
                JwtValidators.createDefaultWithIssuer("collectra-api");
        OAuth2TokenValidator<Jwt> audience =
                new JwtClaimValidator<List<String>>(
                        "aud", values -> values != null && values.contains("collectra-api"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        java.util.ArrayList<GrantedAuthority> authorities = new java.util.ArrayList<>();
        String tokenType = jwt.getClaimAsString("token_type");
        if ("user".equals(tokenType)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_HUMAN"));
        } else if ("service".equals(tokenType)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SERVICE"));
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) java.util.Arrays.stream(scope.split(" ")).filter(s -> !s.isBlank())
                .forEach(code -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + code)));
        return List.copyOf(authorities);
    }
}
