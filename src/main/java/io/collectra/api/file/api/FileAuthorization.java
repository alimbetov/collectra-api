package io.collectra.api.file.api;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class FileAuthorization {

    public boolean canUpload(Authentication authentication) {
        return has(authentication, "ROLE_HUMAN", "FILE_UPLOAD")
                || has(authentication, "ROLE_SERVICE", "SCOPE_file:upload");
    }

    public boolean canRead(Authentication authentication) {
        return has(authentication, "ROLE_HUMAN", "FILE_READ")
                || has(authentication, "ROLE_SERVICE", "SCOPE_file:read");
    }

    public boolean canDelete(Authentication authentication) {
        return has(authentication, "ROLE_HUMAN", "FILE_DELETE")
                || has(authentication, "ROLE_SERVICE", "SCOPE_file:delete");
    }

    public boolean canAdmin(Authentication authentication) {
        return has(authentication, "ROLE_PLATFORM_SUPER_ADMIN")
                || has(authentication, "ROLE_HUMAN", "FILE_ADMIN");
    }

    private boolean has(Authentication authentication, String... required) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> granted =
                authentication.getAuthorities().stream()
                        .map(value -> value.getAuthority())
                        .collect(Collectors.toSet());
        return java.util.Arrays.stream(required).allMatch(granted::contains);
    }
}
