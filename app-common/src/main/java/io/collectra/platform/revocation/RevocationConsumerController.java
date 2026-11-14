package io.collectra.platform.revocation;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Pull contract for trusted resource servers; ACK only after durable local enforcement. */
@RestController
@RequestMapping("/api/v1/internal/revocations")
public class RevocationConsumerController {
    private final JdbcClient db;
    public RevocationConsumerController(JdbcClient db) { this.db=db; }

    @GetMapping("/{subject}")
    @PreAuthorize("hasAuthority('SCOPE_revocations.read')")
    public Map<String,Object> snapshot(@PathVariable String subject,@RequestParam String issuer) {
        return db.sql("SELECT state,version FROM subject_access_state WHERE issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject)
            .query((rs,n)->Map.<String,Object>of("state",rs.getString("state"),
                "version",rs.getLong("version"))).optional()
            .orElse(Map.of("state","ACTIVE","version",0L));
    }

    @PostMapping("/ack")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_revocations.ack')")
    public void ack(@RequestBody Ack ack) {
        // Registration of consumers is an operator-owned operation, not implicit on ACK.
        int n=db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version) " +
            "VALUES (:c,:iss,:sub,:v) ON CONFLICT (consumer_id,issuer,subject_id) " +
            "DO UPDATE SET version=GREATEST(revocation_acks.version,EXCLUDED.version),applied_at=now()")
            .param("c",ack.consumerId()).param("iss",ack.issuer())
            .param("sub",ack.subject()).param("v",ack.version()).update();
    }
    public record Ack(String consumerId,String issuer,String subject,long version) {}
}
