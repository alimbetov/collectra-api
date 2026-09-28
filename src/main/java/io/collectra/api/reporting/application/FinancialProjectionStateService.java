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
public class FinancialProjectionStateService {
    private final JdbcTemplate jdbc;

    public FinancialProjectionStateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryMarkBuilding(
            UUID tenantId, LocalDate day, Instant calculatedAt, Instant staleBefore) {
        return jdbc.update(
                        """
                        insert into tenant_financial_projection_state(
                            tenant_id,business_date,status,revision,metric_rows,calculated_at,error_message)
                        values (?,?,'BUILDING',0,0,?,null)
                        on conflict (tenant_id,business_date) do update set
                            status='BUILDING', calculated_at=excluded.calculated_at, error_message=null
                        where tenant_financial_projection_state.status <> 'BUILDING'
                           or tenant_financial_projection_state.calculated_at < ?
                        """,
                        tenantId,
                        day,
                        Timestamp.from(calculatedAt),
                        Timestamp.from(staleBefore))
                == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID tenantId, LocalDate day, Instant at, String error) {
        String bounded = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        jdbc.update(
                """
                insert into tenant_financial_projection_state(
                    tenant_id,business_date,status,revision,metric_rows,calculated_at,error_message)
                values (?,?,'FAILED',0,0,?,?)
                on conflict (tenant_id,business_date) do update set
                    status='FAILED', calculated_at=excluded.calculated_at,
                    error_message=excluded.error_message
                """,
                tenantId,
                day,
                Timestamp.from(at),
                bounded);
    }
}
