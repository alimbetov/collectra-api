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
    public UUID tryMarkBuilding(
            UUID tenantId, LocalDate day, Instant calculatedAt, Instant staleBefore) {
        UUID buildId = UUID.randomUUID();
        return jdbc.update(
                                """
                                insert into tenant_financial_projection_state(
                                    tenant_id,business_date,status,build_id,revision,metric_rows,calculated_at,error_message)
                                values (?,?,'BUILDING',?,0,0,?,null)
                                on conflict (tenant_id,business_date) do update set
                                    status='BUILDING', build_id=excluded.build_id, calculated_at=excluded.calculated_at, error_message=null
                                where tenant_financial_projection_state.status <> 'BUILDING'
                                   or tenant_financial_projection_state.calculated_at < ?
                                """,
                                tenantId,
                                day,
                                buildId,
                                Timestamp.from(calculatedAt),
                                Timestamp.from(staleBefore))
                        == 1
                ? buildId
                : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(
            UUID tenantId, LocalDate day, UUID buildId, Instant at, String error) {
        String bounded = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        return jdbc.update(
                                """
                                update tenant_financial_projection_state
                                   set status='FAILED', build_id=null, calculated_at=?, error_message=?
                                 where tenant_id=? and business_date=?
                                   and status='BUILDING' and build_id=?
                                """,
                                Timestamp.from(at),
                                bounded,
                                tenantId,
                                day,
                                buildId)
                        == 1;
    }
}
