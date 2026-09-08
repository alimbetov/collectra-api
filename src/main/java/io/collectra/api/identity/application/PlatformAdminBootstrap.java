package io.collectra.api.identity.application;

import io.collectra.api.audit.application.SecurityAuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminBootstrap implements ApplicationRunner {
    private final PlatformAdministratorService administrators;
    private final SecurityAuditService audit;
    private final boolean enabled;
    private final String email;
    private final String password;

    public PlatformAdminBootstrap(PlatformAdministratorService administrators,
            SecurityAuditService audit,
            @Value("${collectra.security.platform-bootstrap.enabled:false}") boolean enabled,
            @Value("${collectra.security.platform-bootstrap.email:}") String email,
            @Value("${collectra.security.platform-bootstrap.password:}") String password) {
        this.administrators = administrators;
        this.audit = audit;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        if (email == null || email.isBlank() || password == null || password.length() < 12
                || password.length() > 72) {
            throw new IllegalStateException("Platform bootstrap email and a 12-72 character password are required");
        }
        administrators.bootstrap(email, password).ifPresent(user ->
                audit.append(null, "SYSTEM", user.getId(), "PLATFORM_ADMIN_BOOTSTRAPPED",
                        "SUCCEEDED", null));
    }
}
