package io.collectra.api.shared.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
@ConditionalOnProperty(name="collectra.security.keycloak.enabled",havingValue="true")
public class KeycloakJwtDecoderConfiguration {
    @Bean
    JwtDecoder keycloakJwtDecoder(
            @Value("${collectra.security.keycloak.issuer}") String issuer,
            @Value("${collectra.security.keycloak.jwk-set-uri}") String jwks,
            KeycloakIdentityGate gate) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> audience=jwt ->
            jwt.getAudience().contains("collectra-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Wrong audience",null));
        OAuth2TokenValidator<Jwt> linked=jwt ->
            gate.isVerifiedActive(jwt.getIssuer().toString(),jwt.getSubject())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Unverified identity link",null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),audience,linked));
        return decoder;
    }
}
