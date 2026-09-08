package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.PlatformUserRoleRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdministratorService {
    private final UserAccountRepository users;
    private final PlatformUserRoleRepository platformRoles;
    private final PasswordEncoder passwords;

    public PlatformAdministratorService(UserAccountRepository users,
            PlatformUserRoleRepository platformRoles, PasswordEncoder passwords) {
        this.users = users;
        this.platformRoles = platformRoles;
        this.passwords = passwords;
    }

    @Transactional
    public Optional<UserAccount> bootstrap(String email, String password) {
        if (platformRoles.activeSuperAdminExists()) return Optional.empty();
        UserAccount user = users.saveAndFlush(new UserAccount(null,
                email.trim().toLowerCase(Locale.ROOT), passwords.encode(password),
                SystemRole.PLATFORM_SUPER_ADMIN));
        platformRoles.assignSuperAdmin(user.getId());
        return Optional.of(user);
    }
}
