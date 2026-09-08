package io.collectra.api.audit.application;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {
    private final JdbcTemplate jdbc;
    public SecurityAuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(UUID tenantId, String actorType, UUID actorId, String action, String result, String reason) {
        jdbc.update("""
                insert into security_audit_events(id, tenant_id, actor_type, actor_id, action, result,
                    reason, trace_id, correlation_id, metadata, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, now())
                "", UUID.randomUUID(), tenantId, actorType, actorId, action, result, reason,
                MDC.get("traceId"), MDC.get("correlationId"));
    }
}
