package io.collectra.api.audit.api;

import io.collectra.api.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit/security-events")
@PreAuthorize("hasAuthority('ROLE_HUMAN')")
public class SecurityAuditController {
    private final JdbcTemplate jdbc;
    public SecurityAuditController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_HUMAN') and hasAuthority('AUDIT_READ')")
    List<AuditEventResponse> find(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.query("""
                select id, actor_type, actor_id, action, result, reason, trace_id, correlation_id, created_at
                  from security_audit_events where tenant_id = ? order by created_at desc limit ?
                """, (rs, row) -> new AuditEventResponse(rs.getObject("id", UUID.class), rs.getString("actor_type"),
                rs.getObject("actor_id", UUID.class), rs.getString("action"), rs.getString("result"),
                rs.getString("reason"), rs.getString("trace_id"), rs.getString("correlation_id"),
                rs.getTimestamp("created_at").toInstant()), TenantContext.requireTenantId(), safeLimit);
    }

    record AuditEventResponse(UUID id, String actorType, UUID actorId, String action, String result,
            String reason, String traceId, String correlationId, Instant createdAt) {}
}
