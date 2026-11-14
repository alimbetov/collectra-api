package io.collectra.platform.revocation;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/operations")
public class RevocationOperationController {
    private final JdbcClient db;
    public RevocationOperationController(JdbcClient db) { this.db = db; }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_platform.subject.read')")
    public Map<String,Object> get(@PathVariable UUID id) {
        return db.sql("SELECT id,state,target_version FROM enforcement_operations WHERE id=:id")
            .param("id",id).query((rs,n) -> Map.<String,Object>of(
                "operationId",rs.getObject("id",UUID.class),
                "state",rs.getString("state"),
                "version",rs.getLong("target_version")))
            .optional().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
