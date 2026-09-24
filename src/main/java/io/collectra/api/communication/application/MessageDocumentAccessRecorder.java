package io.collectra.api.communication.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageDocumentAccessRecorder {
    private final JdbcTemplate jdbc;

    public MessageDocumentAccessRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenantId, UUID linkId, Instant now) {
        jdbc.update(
                """
                update message_document_links
                   set access_count = access_count + 1,
                       first_access_at = coalesce(first_access_at, ?),
                       last_access_at = ?
                 where id = ?
                   and tenant_id = ?
                   and status = 'READY'
                """,
                now,
                now,
                linkId,
                tenantId);
    }
}
