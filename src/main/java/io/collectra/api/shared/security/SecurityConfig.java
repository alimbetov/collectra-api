package io.collectra.api.shared.security;

import io.collectra.api.shared.tenant.TenantContextFilter;
import javax.crypto.SecretKey; import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets; import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/**","/actuator/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwtConverter()))).addFilterAfter(new TenantContextFilter(),BearerTokenAuthenticationFilter.class).build();}
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean SecretKey jwtKey(@Value("${collectra.security.jwt-secret}") String secret){if(secret.length()<32)throw new IllegalStateException("JWT secret must contain at least 32 characters");return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");}
    @Bean JwtEncoder jwtEncoder(SecretKey key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
    @Bean JwtDecoder jwtDecoder(SecretKey key){NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("collectra-api"));return decoder;}
    private Converter<Jwt,? extends org.springframework.security.authentication.AbstractAuthenticationToken> jwtConverter(){JwtAuthenticationConverter c=new JwtAuthenticationConverter();c.setJwtGrantedAuthoritiesConverter(jwt->{List<String> roles=jwt.getClaimAsStringList("roles");return roles==null?List.of():roles.stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList();});return c;}
}
