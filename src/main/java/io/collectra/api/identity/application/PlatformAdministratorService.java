package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.SystemRole;
import io.collectra.api.identity.domain.UserAccount;
import io.collectra.api.identity.infrastructure.PlatformUserRoleRepository;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.UserAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdministratorService {
    private final UserAccountRepository users;
    private final PlatformUserRoleRepository platformRoles;
    private final PasswordEncoder passwords;
    private final RefreshSessionRepository sessions;

    public PlatformAdministratorService(UserAccountRepository users,
            PlatformUserRoleRepository platformRoles, PasswordEncoder passwords,
            RefreshSessionRepository sessions) {
        this.users = users;
        this.platformRoles = platformRoles;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    @Transactional
    public Optional<UserAccount> bootstrap(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<UserAccount> existing = users.findByTenantIdIsNullAndEmailIgnoreCase(normalized);
        UserAccount user =
                existing.orElseGet(
                        () ->
                                users.saveAndFlush(
                                        new UserAccount(
                                                null,
                                                normalized,
                                                passwords.encode(password),
                                                SystemRole.PLATFORM_SUPER_ADMIN)));
        boolean changed = existing.isEmpty();
        if (!"ACTIVE".equals(user.getStatus())) {
            user.activate();
            changed = true;
        }
        if (!platformRoles.hasSuperAdminRole(user.getId())) {
            platformRoles.assignSuperAdmin(user.getId());
            user.authorizationChanged();
            changed = true;
        }
        if (!passwords.matches(password, user.getPasswordHash())) {
            user.changePassword(passwords.encode(password));
            sessions.revokeAllPlatformByUserId(user.getId());
            changed = true;
        }
        return changed ? Optional.of(user) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<UserAccount> administrators() {
        return users.findAllByTenantIdIsNullOrderByEmailAsc().stream()
                .filter(user -> platformRoles.hasSuperAdminRole(user.getId()))
                .toList();
    }

    @Transactional
    public UserAccount create(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByTenantIdIsNullAndEmailIgnoreCase(normalized).isPresent())
            throw new IllegalArgumentException("Platform email already exists");
        UserAccount user = users.saveAndFlush(new UserAccount(null, normalized,
                passwords.encode(password), SystemRole.PLATFORM_SUPER_ADMIN));
        platformRoles.assignSuperAdmin(user.getId());
        return user;
    }

    @Transactional
    public UserAccount changeStatus(UUID actorId, UUID userId, boolean active) {
        return changeStatus(actorId, userId, active, null);
    }

    @Transactional
    public UserAccount changeStatus(
            UUID actorId, UUID userId, boolean active, Long expectedRevision) {
        UserAccount user = requireAdministrator(userId);
        if (expectedRevision != null && user.getVersion() != expectedRevision) {
            throw new io.collectra.api.shared.error.BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Administrator revision conflict: expected "
                            + expectedRevision
                            + " but was "
                            + user.getVersion());
        }
        if (!active) {
            platformRoles.lockSuperAdminRole();
            if (actorId.equals(userId) || ("ACTIVE".equals(user.getStatus())
                    && platformRoles.activeSuperAdminCount() <= 1))
                throw new IllegalArgumentException("The last platform administrator is protected");
            user.block();
            sessions.revokeAllPlatformByUserId(userId);
        } else if (!"ACTIVE".equals(user.getStatus())) {
            user.activate();
        }
        return user;
    }

    @Transactional
    public void changePassword(UUID userId, String password) {
        UserAccount user = requireAdministrator(userId);
        user.changePassword(passwords.encode(password));
        sessions.revokeAllPlatformByUserId(userId);
    }

    @Transactional
    public void removeRole(UUID actorId, UUID userId) {
        removeRole(actorId, userId, null);
    }

    @Transactional
    public void removeRole(UUID actorId, UUID userId, Long expectedRevision) {
        UserAccount user = requireAdministrator(userId);
        if (expectedRevision != null && user.getVersion() != expectedRevision) {
            throw new io.collectra.api.shared.error.BusinessConflictException(
                    "VERSION_CONFLICT",
                    "Administrator revision conflict: expected "
                            + expectedRevision
                            + " but was "
                            + user.getVersion());
        }
        platformRoles.lockSuperAdminRole();
        if (actorId.equals(userId) || ("ACTIVE".equals(user.getStatus())
                && platformRoles.activeSuperAdminCount() <= 1))
            throw new IllegalArgumentException("The last platform administrator is protected");
        platformRoles.removeSuperAdmin(userId);
        user.authorizationChanged();
        sessions.revokeAllPlatformByUserId(userId);
    }

    private UserAccount requireAdministrator(UUID userId) {
        UserAccount user = users.findByIdAndTenantIdIsNull(userId)
                .orElseThrow(() -> new NoSuchElementException("Platform administrator not found"));
        if (!platformRoles.hasSuperAdminRole(userId))
            throw new NoSuchElementException("Platform administrator not found");
        return user;
    }
}
