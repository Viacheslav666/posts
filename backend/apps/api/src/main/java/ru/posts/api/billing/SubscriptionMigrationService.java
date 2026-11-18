package ru.posts.api.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expand-only migration helper: remaining paid subscription period → wallet credit; flip
 * {@code billing_model} to {@code balance}.
 */
@Service
@Profile("!openapi")
public class SubscriptionMigrationService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionMigrationService.class);

    private final JdbcClient jdbc;
    private final WalletLedgerService walletLedgerService;

    public SubscriptionMigrationService(javax.sql.DataSource dataSource, WalletLedgerService walletLedgerService) {
        this.jdbc = JdbcClient.create(dataSource);
        this.walletLedgerService = walletLedgerService;
    }

    @Transactional
    public int migrateActiveSubscriptions(Instant now) {
        List<Row> rows = jdbc.sql(
                        """
                        SELECT s.workspace_id, s.period_end, p.price_month, s.billing_period
                        FROM subscriptions s
                        JOIN plans p ON p.id = s.plan_id
                        JOIN workspaces w ON w.id = s.workspace_id
                        WHERE s.status = 'active'
                          AND s.period_end > :now
                          AND w.billing_model = 'subscription'
                          AND w.deleted_at IS NULL
                        """)
                .param("now", now)
                .query((rs, n) -> new Row(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getTimestamp("period_end").toInstant(),
                        rs.getBigDecimal("price_month"),
                        rs.getString("billing_period")))
                .list();

        int migrated = 0;
        for (Row row : rows) {
            long remainingDays = Math.max(0, ChronoUnit.DAYS.between(now, row.periodEnd()));
            if (remainingDays <= 0) {
                continue;
            }
            BigDecimal month = row.priceMonth() == null ? BigDecimal.ZERO : row.priceMonth();
            long creditKopecks = month.multiply(BigDecimal.valueOf(remainingDays))
                    .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            if (creditKopecks > 0) {
                walletLedgerService.credit(
                        row.workspaceId(),
                        creditKopecks,
                        "payment",
                        false,
                        "migration:" + row.workspaceId() + ":" + row.periodEnd().getEpochSecond(),
                        null,
                        "Subscription remainder → balance");
            }
            jdbc.sql(
                            """
                            UPDATE workspaces
                            SET billing_model = 'balance', updated_at = now()
                            WHERE id = :id
                            """)
                    .param("id", row.workspaceId())
                    .update();
            migrated++;
        }
        log.info("Migrated {} workspaces from subscription to balance", migrated);
        return migrated;
    }

    private record Row(UUID workspaceId, Instant periodEnd, BigDecimal priceMonth, String billingPeriod) {}
}
