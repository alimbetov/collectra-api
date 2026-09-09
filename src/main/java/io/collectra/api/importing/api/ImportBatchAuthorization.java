package io.collectra.api.importing.api;

import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ImportBatchAuthorization {
    public boolean canCreate(Authentication authentication) {
        return has(authentication, "ROLE_HUMAN", "DOCUMENT_GENERATE")
                || has(authentication, "ROLE_SERVICE", "SCOPE_document:generate");
    }

    public boolean canRead(Authentication authentication) {
        return has(authentication, "ROLE_HUMAN", "DOCUMENT_READ")
                || has(authentication, "ROLE_SERVICE", "SCOPE_document:read");
    }

    private boolean has(Authentication authentication, String... required) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        Set<String> granted = authentication.getAuthorities().stream()
                .map(value -> value.getAuthority()).collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(required).allMatch(granted::contains);
    }
}
