package io.collectra.api.shared.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring Security owns the filter order; disable the extra servlet-container registration. */
@Configuration
public class KeycloakFilterRegistrationConfiguration {
    @Bean
    FilterRegistrationBean<KeycloakDomainAuthorizationFilter> keycloakDomainFilterRegistration(
            KeycloakDomainAuthorizationFilter filter) {
        FilterRegistrationBean<KeycloakDomainAuthorizationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
