package io.collectra.api.platform.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOverviewQueryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PlatformOverviewQueryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        Instant generatedAt = clock.instant();
        Instant from = generatedAt.minus(java.time.Duration.ofDays(30));
        Timestamp fromSql = Timestamp.from(from);

        long tenantsTotal = count("SELECT COUNT(*) FROM tenants");
        long tenantsActive = count("SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE'");
        long tenantsBlocked = count("SELECT COUNT(*) FROM tenants WHERE status = 'BLOCKED'");
        long usersTotal = count("SELECT COUNT(*) FROM tenant_memberships");
        long usersActive =
                count(
                        """
                        SELECT COUNT(*)
                        FROM tenant_memberships tm
                        JOIN user_accounts ua ON ua.id = tm.user_id
                        WHERE tm.status = 'ACTIVE'
                          AND ua.status = 'ACTIVE'
                        """);
        long usersBlocked =
                count(
                        """
                        SELECT COUNT(*)
                        FROM tenant_memberships tm
                        JOIN user_accounts ua ON ua.id = tm.user_id
                        WHERE tm.status <> 'ACTIVE'
                           OR ua.status <> 'ACTIVE'
                        """);
        long campaignsLast30Days =
                count("SELECT COUNT(*) FROM campaigns WHERE created_at >= ?", fromSql);
        long messagesLast30Days =
                count("SELECT COUNT(*) FROM messages WHERE created_at >= ?", fromSql);
        long sentLast30Days =
                count(
                        "SELECT COUNT(*) FROM messages WHERE created_at >= ? AND status = 'SENT'",
                        fromSql);
        long failedLast30Days =
                count(
                        "SELECT COUNT(*) FROM messages WHERE created_at >= ? AND status = 'FAILED'",
                        fromSql);
        long retryWaitCurrent = count("SELECT COUNT(*) FROM messages WHERE status = 'RETRY_WAIT'");
        long unknownCurrent = count("SELECT COUNT(*) FROM messages WHERE status = 'UNKNOWN'");

        List<ChannelCount> channels =
                jdbc.query(
                        """
                        SELECT channel, COUNT(*) AS message_count
                        FROM messages
                        WHERE created_at >= ?
                        GROUP BY channel
                        ORDER BY channel
                        """,
                        (rs, rowNum) ->
                                new ChannelCount(
                                        rs.getString("channel"), rs.getLong("message_count")),
                        fromSql);

        return new Overview(
                generatedAt,
                from,
                tenantsTotal,
                tenantsActive,
                tenantsBlocked,
                usersTotal,
                usersActive,
                usersBlocked,
                campaignsLast30Days,
                messagesLast30Days,
                sentLast30Days,
                failedLast30Days,
                retryWaitCurrent,
                unknownCurrent,
                channels);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    public record ChannelCount(String channel, long messages) {}

    public record Overview(
            Instant generatedAt,
            Instant periodFrom,
            long tenantsTotal,
            long tenantsActive,
            long tenantsBlocked,
            long usersTotal,
            long usersActive,
            long usersBlocked,
            long campaignsLast30Days,
            long messagesLast30Days,
            long sentLast30Days,
            long failedLast30Days,
            long retryWaitCurrent,
            long unknownCurrent,
            List<ChannelCount> channels) {}
}
