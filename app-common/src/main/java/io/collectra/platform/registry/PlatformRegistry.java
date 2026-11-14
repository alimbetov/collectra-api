package io.collectra.platform.registry;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformRegistry {
    private final JdbcClient db;
    public PlatformRegistry(JdbcClient db) { this.db = db; }

    @Transactional
    public Map<String, Object> register(String code) {
        if (code == null || !code.matches("[a-z][a-z0-9-]{2,79}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid application code");
        UUID id = UUID.randomUUID();
        try {
            db.sql("INSERT INTO applications(id,code,state) VALUES (:id,:code,'REGISTERED')")
                .param("id", id).param("code", code).update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application code already exists");
        }
        return Map.of("id", id, "code", code, "state", "REGISTERED");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> find(UUID id) {
        return db.sql("SELECT id,code,state,version FROM applications WHERE id=:id")
            .param("id", id).query((rs, row) -> Map.<String,Object>of(
                "id", rs.getObject("id", UUID.class), "code", rs.getString("code"),
                "state", rs.getString("state"), "version", rs.getLong("version")))
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
