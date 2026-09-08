package io.collectra.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityDomainUnitTest {
    @Test
    void membershipCanBeBlockedAndActivated() {
        TenantMembership membership = new TenantMembership(UUID.randomUUID(), UUID.randomUUID());
        assertThat(membership.active()).isTrue();
        membership.block();
        assertThat(membership.active()).isFalse();
        assertThat(membership.getStatus()).isEqualTo("BLOCKED");
        membership.activate();
        assertThat(membership.active()).isTrue();
    }

    @Test
    void customRoleIsTenantScopedAndNotSystemRole() {
        UUID tenantId = UUID.randomUUID();
        Role role = new Role(tenantId, "COLLECTION_MANAGER");
        assertThat(role.getTenantId()).isEqualTo(tenantId);
        assertThat(role.getScopeType()).isEqualTo("TENANT");
        assertThat(role.isSystemRole()).isFalse();
    }

    @Test
    void refreshReplacementRevokesPredecessor() {
        RefreshSession session = new RefreshSession(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "a".repeat(64), UUID.randomUUID(), Instant.now().plusSeconds(60));
        UUID successor = UUID.randomUUID();
        assertThat(session.active()).isTrue();
        session.replaceWith(successor);
        assertThat(session.revoked()).isTrue();
        assertThat(session.getReplacedById()).isEqualTo(successor);
    }

    @Test
    void expiredRefreshSessionIsNotActive() {
        RefreshSession session = new RefreshSession(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "b".repeat(64), UUID.randomUUID(), Instant.now().minusSeconds(1));
        assertThat(session.active()).isFalse();
    }

    @Test
    void otpIsSingleUseAndCountsInvalidAttempts() {
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID(), "USER", UUID.randomUUID(),
                "LOGOUT_ALL", "expected-hash");
        assertThat(challenge.verify("wrong-hash")).isFalse();
        assertThat(challenge.verify("expected-hash")).isTrue();
        assertThat(challenge.verify("expected-hash")).isFalse();
    }
}
