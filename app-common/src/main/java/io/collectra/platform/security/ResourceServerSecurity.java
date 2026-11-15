package io.collectra.platform.security;

import org.springframework.beans.factory.annotation.Value;
import io.collectra.platform.revocation.RevocationTokenValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableMethodSecurity
public class ResourceServerSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain browserOidcSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/v1/iam/**", "/oauth2/authorization/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/iam/oidc/config", "/api/v1/iam/login",
                    "/api/v1/iam/callback", "/oauth2/authorization/**").permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .oauth2Login(oidc -> oidc
                .redirectionEndpoint(redirect -> redirect.baseUri("/api/v1/iam/callback"))
                .defaultSuccessUrl("/api/v1/iam/session", true))
            .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/api/v1/iam/oidc/config").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
        @Value("${app-common.security.issuer-uri}") String issuer,
        @Value("${app-common.security.jwk-set-uri}") String jwkSetUri,
        @Value("${app-common.security.audience}") String audience,
        RevocationTokenValidator revocation) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceValidator = requiredAudience(audience);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceValidator, revocation));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> requiredAudience(String audience) {
        return jwt -> jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "Required API audience is absent", null));
    }
}
