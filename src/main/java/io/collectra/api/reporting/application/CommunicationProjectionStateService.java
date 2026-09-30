package io.collectra.api.reporting.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationProjectionStateService {
    private final JdbcTemplate jdbc;

    public CommunicationProjectionStateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID tryMarkBuilding(
            UUID tenantId, LocalDate businessDate, Instant calculatedAt, Instant staleBefore) {
        UUID buildId = UUID.randomUUID();
        int changed =
                jdbc.update(
                        """
                        insert into communication_reporting_projection_state(
                            tenant_id,
                            business_date,
                            status,
                            revision,
                            campaign_rows,
                            failure_rows,
                            calculated_at,
                            error_message,
                            build_id
                        )
                        values (?, ?, 'BUILDING', 0, 0, 0, ?, null, ?)
                        on conflict (tenant_id, business_date)
                        do update set
                            status = 'BUILDING',
                            calculated_at = excluded.calculated_at,
                            error_message = null,
                            build_id = excluded.build_id
                        where communication_reporting_projection_state.status <> 'BUILDING'
                           or communication_reporting_projection_state.calculated_at < ?
                        """,
                        tenantId,
                        businessDate,
                        Timestamp.from(calculatedAt),
                        buildId,
                        Timestamp.from(staleBefore));
        return changed == 1 ? buildId : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(
            UUID tenantId,
            LocalDate businessDate,
            UUID buildId,
            Instant calculatedAt,
            String errorMessage) {
        return jdbc.update(
                        """
                        update communication_reporting_projection_state
                           set status = 'FAILED',
                               build_id = null,
                               calculated_at = ?,
                               error_message = ?
                         where tenant_id = ?
                           and business_date = ?
                           and status = 'BUILDING'
                           and build_id = ?
                        """,
                        Timestamp.from(calculatedAt),
                        truncate(errorMessage, 1000),
                        tenantId,
                        businessDate,
                        buildId)
                == 1;
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
