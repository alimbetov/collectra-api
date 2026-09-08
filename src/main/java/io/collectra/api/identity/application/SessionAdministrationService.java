package io.collectra.api.identity.application;

import io.collectra.api.identity.domain.RefreshSession;
import io.collectra.api.identity.domain.TenantMembership;
import io.collectra.api.identity.infrastructure.RefreshSessionRepository;
import io.collectra.api.identity.infrastructure.TenantMembershipRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionAdministrationService {
    private final TenantMembershipRepository memberships;
    private final RefreshSessionRepository sessions;

    public SessionAdministrationService(TenantMembershipRepository memberships,
            RefreshSessionRepository sessions) {
        this.memberships = memberships;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public List<RefreshSession> sessions(UUID tenantId, UUID membershipId) {
        TenantMembership membership = membership(tenantId, membershipId);
        return sessions.findAllByUserIdAndMembershipIdOrderByCreatedAtDesc(
                membership.getUserId(), membershipId);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID membershipId, UUID sessionId) {
        TenantMembership membership = membership(tenantId, membershipId);
        sessions.findByIdAndUserIdAndMembershipId(sessionId, membership.getUserId(), membershipId)
                .orElseThrow(() -> new NoSuchElementException("Session not found"))
                .revoke();
    }

    @Transactional
    public void revokeAll(UUID tenantId, UUID membershipId) {
        membership(tenantId, membershipId);
        sessions.revokeAllByMembershipId(membershipId);
    }

    private TenantMembership membership(UUID tenantId, UUID membershipId) {
        return memberships.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Membership not found"));
    }
}
