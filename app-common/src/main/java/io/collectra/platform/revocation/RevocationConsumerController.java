package io.collectra.platform.revocation;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

/** Pull contract for trusted resource servers; ACK only after durable local enforcement. */
@RestController
@RequestMapping("/api/v1/internal/revocations")
public class RevocationConsumerController {
    private final JdbcClient db;
    public RevocationConsumerController(JdbcClient db) { this.db=db; }
    private String consumer(Jwt jwt) {
        String client=jwt.getClaimAsString("azp");
        if(client==null || client.isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return db.sql("SELECT consumer_id FROM revocation_consumers WHERE oauth_client_id=:client AND enabled=true")
            .param("client",client).query(String.class).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    @GetMapping("/{subject}")
    @PreAuthorize("hasAuthority('SCOPE_revocations.read')")
    public Map<String,Object> snapshot(@AuthenticationPrincipal Jwt jwt,@PathVariable String subject,@RequestParam String issuer) {
        consumer(jwt);
        return db.sql("SELECT state,version FROM subject_access_state WHERE issuer=:iss AND subject_id=:sub")
            .param("iss",issuer).param("sub",subject)
            .query((rs,n)->Map.<String,Object>of("state",rs.getString("state"),
                "version",rs.getLong("version"))).optional()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Subject not initialized"));
    }

    @PostMapping("/ack")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('SCOPE_revocations.ack')")
    public void ack(@AuthenticationPrincipal Jwt jwt,@RequestBody Ack ack) {
        String caller=consumer(jwt);
        if(ack.issuer()==null || ack.subject()==null || ack.version()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long current=db.sql("SELECT version FROM subject_access_state WHERE issuer=:iss AND subject_id=:sub")
            .param("iss",ack.issuer()).param("sub",ack.subject()).query(Long.class)
            .optional().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT));
        if(ack.version()>current) throw new ResponseStatusException(HttpStatus.CONFLICT);
        db.sql("INSERT INTO revocation_acks(consumer_id,issuer,subject_id,version) " +
            "VALUES (:c,:iss,:sub,:v) ON CONFLICT (consumer_id,issuer,subject_id) " +
            "DO UPDATE SET version=GREATEST(revocation_acks.version,EXCLUDED.version),applied_at=now()")
            .param("c",caller).param("iss",ack.issuer())
            .param("sub",ack.subject()).param("v",ack.version()).update();
    }
    public record Ack(String issuer,String subject,long version) {}

}
