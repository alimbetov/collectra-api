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
    public boolean tryMarkBuilding(
            UUID tenantId, LocalDate businessDate, Instant calculatedAt, Instant staleBefore) {
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
                            error_message
                        )
                        values (?, ?, 'BUILDING', 0, 0, 0, ?, null)
                        on conflict (tenant_id, business_date)
                        do update set
                            status = 'BUILDING',
                            calculated_at = excluded.calculated_at,
                            error_message = null
                        where communication_reporting_projection_state.status <> 'BUILDING'
                           or communication_reporting_projection_state.calculated_at < ?
                        """,
                        tenantId,
                        businessDate,
                        Timestamp.from(calculatedAt),
                        Timestamp.from(staleBefore));
        return changed == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            UUID tenantId, LocalDate businessDate, Instant calculatedAt, String errorMessage) {
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
                    error_message
                )
                values (?, ?, 'FAILED', 0, 0, 0, ?, ?)
                on conflict (tenant_id, business_date)
                do update set
                    status = 'FAILED',
                    calculated_at = excluded.calculated_at,
                    error_message = excluded.error_message
                """,
                tenantId,
                businessDate,
                Timestamp.from(calculatedAt),
                truncate(errorMessage, 1000));
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
