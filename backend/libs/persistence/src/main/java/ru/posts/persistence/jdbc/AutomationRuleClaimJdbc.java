package ru.posts.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims due grabber/rss automation rules with {@code FOR UPDATE SKIP LOCKED} for distributed
 * scheduler instances (100k users ×3).
 */
public class AutomationRuleClaimJdbc {

    private static final String CLAIM_DUE =
            """
            WITH due AS (
              SELECT id
              FROM automation_rules
              WHERE status = 'active'
                AND type IN ('grabber', 'rss')
                AND next_run_at IS NOT NULL
                AND next_run_at <= :now
              ORDER BY next_run_at
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
            )
            UPDATE automation_rules r
            SET next_run_at = :leaseUntil,
                updated_at = :now
            FROM due
            WHERE r.id = due.id
            RETURNING r.id, r.workspace_id, r.type,
                      coalesce((r.config->>'scan_interval_minutes')::int, 180) AS scan_interval_minutes
            """;

    private final JdbcClient jdbcClient;

    public AutomationRuleClaimJdbc(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public List<DueAutomationRule> claimDue(int batchSize, Instant now, Instant leaseUntil) {
        return jdbcClient
                .sql(CLAIM_DUE)
                .param("now", Timestamp.from(now))
                .param("batchSize", batchSize)
                .param("leaseUntil", Timestamp.from(leaseUntil))
                .query((rs, rowNum) -> new DueAutomationRule(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getString("type"),
                        rs.getInt("scan_interval_minutes")))
                .list();
    }

    public record DueAutomationRule(UUID ruleId, UUID workspaceId, String type, int scanIntervalMinutes) {}
}
