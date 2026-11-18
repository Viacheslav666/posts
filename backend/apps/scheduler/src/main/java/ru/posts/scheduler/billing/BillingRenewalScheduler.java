package ru.posts.scheduler.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.common.uuid.UuidV7;
import ru.posts.yookassa.Amount;
import ru.posts.yookassa.PaymentResponse;
import ru.posts.yookassa.Receipt;
import ru.posts.yookassa.YooKassaClient;
import ru.posts.yookassa.YooKassaException;
import ru.posts.yookassa.YooKassaProperties;

/**
 * Daily billing renewal / grace / freeze / trial expiry (BE-12).
 *
 * <p>Windows: D-1 (period_end tomorrow), D0 (period_end today), D+2 (period_end two days ago —
 * still in grace window before freeze at +3).
 */
@Component
public class BillingRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingRenewalScheduler.class);

    private final JdbcClient jdbcClient;
    private final YooKassaClient yooKassaClient;
    private final YooKassaProperties yooKassaProperties;
    private final int graceDays;

    public BillingRenewalScheduler(
            JdbcClient jdbcClient,
            YooKassaClient yooKassaClient,
            YooKassaProperties yooKassaProperties,
            @Value("${posts.billing.grace-days:3}") int graceDays) {
        this.jdbcClient = jdbcClient;
        this.yooKassaClient = yooKassaClient;
        this.yooKassaProperties = yooKassaProperties;
        this.graceDays = graceDays;
    }

    @Scheduled(cron = "${posts.billing.renewal.cron:0 15 4 * * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "billingRenewalDaily", lockAtMostFor = "1h", lockAtLeastFor = "1m")
    public void runDaily() {
        Instant now = Instant.now();
        renewDueSubscriptions(now);
        escalateGraceToFrozen(now);
        expireTrials(now);
    }

    private void renewDueSubscriptions(Instant now) {
        Instant from = now.minus(2, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
        List<SubscriptionRow> due = jdbcClient
                .sql(
                        """
                        SELECT s.id, s.workspace_id, s.plan_id, s.period_end, s.billing_period,
                               s.provider_payment_method_id, p.code AS plan_code,
                               p.price_month, p.price_year, p.name AS plan_name,
                               u.email AS owner_email
                        FROM subscriptions s
                        JOIN plans p ON p.id = s.plan_id
                        JOIN workspaces w ON w.id = s.workspace_id AND w.deleted_at IS NULL
                        JOIN users u ON u.id = w.owner_user_id
                        WHERE s.status = 'active'
                          AND s.cancel_at_period_end = FALSE
                          AND s.provider_payment_method_id IS NOT NULL
                          AND s.period_end >= :fromTs
                          AND s.period_end < :toTs
                        """)
                .param("fromTs", from)
                .param("toTs", to)
                .query((rs, rowNum) -> new SubscriptionRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("plan_id", UUID.class),
                        rs.getTimestamp("period_end").toInstant(),
                        rs.getString("billing_period"),
                        rs.getString("provider_payment_method_id"),
                        rs.getString("plan_code"),
                        rs.getBigDecimal("price_month"),
                        rs.getBigDecimal("price_year"),
                        rs.getString("plan_name"),
                        rs.getString("owner_email")))
                .list();

        int attempted = 0;
        int failed = 0;
        for (SubscriptionRow row : due) {
            // Charge on D-1 / D0 / and retries through D+2 while still active (before grace).
            if (row.periodEnd().isAfter(now.plus(1, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS))) {
                continue;
            }
            attempted++;
            if (!chargeRenewal(row, now)) {
                failed++;
                markPastDue(row, now);
            }
        }
        log.info("Billing renewal: candidates={}, attempted={}, failed={}", due.size(), attempted, failed);
    }

    private boolean chargeRenewal(SubscriptionRow row, Instant now) {
        String period = row.billingPeriod() == null ? "month" : row.billingPeriod();
        BigDecimal amount = "year".equals(period) ? row.priceYear() : row.priceMonth();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        String idempotenceKey = "renewal:" + row.id() + ":" + row.periodEnd().getEpochSecond();
        Amount yooAmount = new Amount(amount.toPlainString(), "RUB");
        String description = row.planName() + " renewal";
        Receipt receipt = new Receipt(
                new Receipt.Customer(row.ownerEmail()),
                List.of(new Receipt.ReceiptItem(description, "1.00", yooAmount, yooKassaProperties.vatCode())));
        Map<String, String> metadata = Map.of(
                "workspaceId",
                row.workspaceId().toString(),
                "planCode",
                row.planCode(),
                "planId",
                row.planId().toString(),
                "period",
                period,
                "subscriptionId",
                row.id().toString(),
                "renewal",
                "true");
        try {
            PaymentResponse response = yooKassaClient.createRecurringPayment(
                    yooAmount, row.providerPaymentMethodId(), description, metadata, receipt, idempotenceKey);
            if ("succeeded".equals(response.status()) || Boolean.TRUE.equals(response.paid())) {
                activateAfterRenewal(row, response, amount, period, now);
                return true;
            }
            if ("pending".equals(response.status()) || "waiting_for_capture".equals(response.status())) {
                insertPendingPayment(row, response, amount, period, idempotenceKey);
                return true;
            }
            log.warn("Renewal payment for subscription {} status={}", row.id(), response.status());
            return false;
        } catch (YooKassaException ex) {
            log.warn("Renewal charge failed for subscription {}: {}", row.id(), ex.getMessage());
            return false;
        }
    }

    private void activateAfterRenewal(
            SubscriptionRow row, PaymentResponse response, BigDecimal amount, String period, Instant now) {
        Instant periodEnd = "year".equals(period) ? now.plus(365, ChronoUnit.DAYS) : now.plus(30, ChronoUnit.DAYS);
        // Prefer anniversary from previous period_end when still close.
        if (row.periodEnd() != null) {
            periodEnd = "year".equals(period)
                    ? row.periodEnd().plus(365, ChronoUnit.DAYS)
                    : row.periodEnd().plus(30, ChronoUnit.DAYS);
        }
        UUID paymentId = UuidV7.generate();
        jdbcClient
                .sql(
                        """
                        INSERT INTO payments (
                            id, workspace_id, provider, provider_payment_id, amount, currency, status,
                            plan_id, billing_period, metadata, idempotence_key, created_at, updated_at
                        ) VALUES (
                            :id, :workspaceId, 'yookassa', :providerPaymentId, :amount, 'RUB', 'succeeded',
                            :planId, :period, CAST(:metadata AS jsonb), :idempotenceKey, now(), now()
                        )
                        ON CONFLICT (provider_payment_id) DO NOTHING
                        """)
                .param("id", paymentId)
                .param("workspaceId", row.workspaceId())
                .param("providerPaymentId", response.id())
                .param("amount", amount)
                .param("planId", row.planId())
                .param("period", period)
                .param("metadata", "{\"subscriptionId\":\"" + row.id() + "\",\"renewal\":true}")
                .param(
                        "idempotenceKey",
                        "renewal:" + row.id() + ":" + row.periodEnd().getEpochSecond())
                .update();

        jdbcClient
                .sql(
                        """
                        UPDATE subscriptions
                        SET status = 'active',
                            period_start = :periodStart,
                            period_end = :periodEnd,
                            grace_ends_at = NULL,
                            frozen_at = NULL,
                            updated_at = now()
                        WHERE id = :id
                        """)
                .param("periodStart", now)
                .param("periodEnd", periodEnd)
                .param("id", row.id())
                .update();

        jdbcClient
                .sql(
                        """
                        UPDATE workspaces
                        SET billing_status = 'active',
                            current_period_end = :periodEnd,
                            updated_at = now()
                        WHERE id = :workspaceId
                        """)
                .param("periodEnd", periodEnd)
                .param("workspaceId", row.workspaceId())
                .update();
    }

    private void insertPendingPayment(
            SubscriptionRow row, PaymentResponse response, BigDecimal amount, String period, String idempotenceKey) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO payments (
                            id, workspace_id, provider, provider_payment_id, amount, currency, status,
                            plan_id, billing_period, metadata, idempotence_key, created_at, updated_at
                        ) VALUES (
                            :id, :workspaceId, 'yookassa', :providerPaymentId, :amount, 'RUB', 'pending',
                            :planId, :period, CAST(:metadata AS jsonb), :idempotenceKey, now(), now()
                        )
                        ON CONFLICT (provider_payment_id) DO NOTHING
                        """)
                .param("id", UuidV7.generate())
                .param("workspaceId", row.workspaceId())
                .param("providerPaymentId", response.id())
                .param("amount", amount)
                .param("planId", row.planId())
                .param("period", period)
                .param("metadata", "{\"renewal\":true}")
                .param("idempotenceKey", idempotenceKey)
                .update();
    }

    private void markPastDue(SubscriptionRow row, Instant now) {
        // Only enter grace once period has ended.
        if (row.periodEnd().isAfter(now)) {
            return;
        }
        Instant graceEnds = now.plus(graceDays, ChronoUnit.DAYS);
        jdbcClient
                .sql(
                        """
                        UPDATE subscriptions
                        SET status = 'past_due',
                            grace_ends_at = COALESCE(grace_ends_at, :graceEnds),
                            updated_at = now()
                        WHERE id = :id AND status = 'active'
                        """)
                .param("graceEnds", graceEnds)
                .param("id", row.id())
                .update();
        jdbcClient
                .sql(
                        """
                        UPDATE workspaces
                        SET billing_status = 'grace',
                            updated_at = now()
                        WHERE id = :workspaceId AND billing_status = 'active'
                        """)
                .param("workspaceId", row.workspaceId())
                .update();
    }

    private void escalateGraceToFrozen(Instant now) {
        List<UUID> workspaces = jdbcClient
                .sql(
                        """
                        SELECT DISTINCT s.workspace_id
                        FROM subscriptions s
                        JOIN workspaces w ON w.id = s.workspace_id
                        WHERE (s.status = 'past_due' AND s.grace_ends_at IS NOT NULL AND s.grace_ends_at <= :now)
                           OR (w.billing_status = 'grace' AND s.grace_ends_at IS NOT NULL AND s.grace_ends_at <= :now)
                        """)
                .param("now", now)
                .query((rs, rowNum) -> rs.getObject("workspace_id", UUID.class))
                .list();

        for (UUID workspaceId : workspaces) {
            jdbcClient
                    .sql(
                            """
                            UPDATE subscriptions
                            SET frozen_at = COALESCE(frozen_at, :now),
                                updated_at = now()
                            WHERE workspace_id = :workspaceId AND status = 'past_due'
                            """)
                    .param("now", now)
                    .param("workspaceId", workspaceId)
                    .update();
            jdbcClient
                    .sql(
                            """
                            UPDATE workspaces
                            SET billing_status = 'frozen',
                                updated_at = now()
                            WHERE id = :workspaceId
                            """)
                    .param("workspaceId", workspaceId)
                    .update();
            jdbcClient
                    .sql(
                            """
                            UPDATE posts
                            SET status = 'paused', updated_at = now()
                            WHERE workspace_id = :workspaceId
                              AND deleted_at IS NULL
                              AND status IN ('scheduled', 'approved')
                            """)
                    .param("workspaceId", workspaceId)
                    .update();
        }
        if (!workspaces.isEmpty()) {
            log.info("Billing freeze applied to {} workspaces", workspaces.size());
        }
    }

    private void expireTrials(Instant now) {
        UUID freePlanId = jdbcClient
                .sql("SELECT id FROM plans WHERE code = 'free' LIMIT 1")
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .optional()
                .orElse(null);
        if (freePlanId == null) {
            return;
        }
        int updated = jdbcClient
                .sql(
                        """
                        UPDATE workspaces
                        SET plan_id = :freePlanId,
                            billing_status = 'active',
                            trial_ends_at = NULL,
                            updated_at = now()
                        WHERE billing_status = 'trial'
                          AND trial_ends_at IS NOT NULL
                          AND trial_ends_at <= :now
                          AND deleted_at IS NULL
                        """)
                .param("freePlanId", freePlanId)
                .param("now", now)
                .update();
        if (updated > 0) {
            jdbcClient
                    .sql(
                            """
                            UPDATE subscriptions s
                            SET status = 'expired',
                                plan_id = :freePlanId,
                                updated_at = now()
                            FROM workspaces w
                            WHERE s.workspace_id = w.id
                              AND w.plan_id = :freePlanId
                              AND s.status = 'trialing'
                              AND s.period_end <= :now
                            """)
                    .param("freePlanId", freePlanId)
                    .param("now", now)
                    .update();
            log.info("Expired {} trial workspaces back to free", updated);
        }
    }

    private record SubscriptionRow(
            UUID id,
            UUID workspaceId,
            UUID planId,
            Instant periodEnd,
            String billingPeriod,
            String providerPaymentMethodId,
            String planCode,
            BigDecimal priceMonth,
            BigDecimal priceYear,
            String planName,
            String ownerEmail) {}
}
