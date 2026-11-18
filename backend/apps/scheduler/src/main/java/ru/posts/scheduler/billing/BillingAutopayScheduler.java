package ru.posts.scheduler.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Autopay: when wallet balance &lt; threshold, charge saved YooKassa payment method (BE-31).
 *
 * <p>Idempotence key {@code autopay:{workspaceId}:{yyyy-MM-dd}} prevents double charges per day.
 */
@Component
public class BillingAutopayScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingAutopayScheduler.class);
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final JdbcClient jdbc;
    private final YooKassaClient yooKassaClient;
    private final YooKassaProperties yooKassaProperties;
    private final int batchSize;

    public BillingAutopayScheduler(
            JdbcClient jdbcClient,
            YooKassaClient yooKassaClient,
            YooKassaProperties yooKassaProperties,
            @Value("${posts.billing.autopay-batch-size:200}") int batchSize) {
        this.jdbc = jdbcClient;
        this.yooKassaClient = yooKassaClient;
        this.yooKassaProperties = yooKassaProperties;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${posts.billing.autopay.cron:0 20 3 * * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "billingAutopay", lockAtMostFor = "1h", lockAtLeastFor = "30s")
    public void runAutopay() {
        LocalDate today = LocalDate.now(MSK);
        List<AutopayRow> due = jdbc.sql(
                        """
                        SELECT a.workspace_id, a.threshold_kopecks, a.topup_amount_kopecks,
                               a.provider_payment_method_id, w.balance_kopecks, u.email AS owner_email
                        FROM autopay_settings a
                        JOIN wallets w ON w.workspace_id = a.workspace_id
                        JOIN workspaces ws ON ws.id = a.workspace_id AND ws.deleted_at IS NULL
                        JOIN users u ON u.id = ws.owner_user_id
                        WHERE a.enabled = TRUE
                          AND a.provider_payment_method_id IS NOT NULL
                          AND w.balance_kopecks < a.threshold_kopecks
                          AND (a.last_triggered_at IS NULL
                               OR a.last_triggered_at < :dayStart)
                        ORDER BY a.workspace_id
                        LIMIT :limit
                        """)
                .param("dayStart", today.atStartOfDay(MSK).toInstant())
                .param("limit", batchSize)
                .query((rs, n) -> new AutopayRow(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getLong("threshold_kopecks"),
                        rs.getLong("topup_amount_kopecks"),
                        rs.getString("provider_payment_method_id"),
                        rs.getLong("balance_kopecks"),
                        rs.getString("owner_email")))
                .list();

        int ok = 0;
        int fail = 0;
        for (AutopayRow row : due) {
            if (charge(row, today)) {
                ok++;
            } else {
                fail++;
            }
        }
        log.info("Billing autopay: candidates={}, succeeded={}, failed={}", due.size(), ok, fail);
    }

    private boolean charge(AutopayRow row, LocalDate today) {
        String idempotenceKey = "autopay:" + row.workspaceId() + ":" + today;
        // Skip if payment with this idempotence already exists
        Integer exists = jdbc.sql("SELECT 1 FROM payments WHERE idempotence_key = :k LIMIT 1")
                .param("k", idempotenceKey)
                .query(Integer.class)
                .optional()
                .orElse(null);
        if (exists != null) {
            return true;
        }

        BigDecimal amount = BigDecimal.valueOf(row.topupAmountKopecks(), 2).setScale(2, RoundingMode.HALF_UP);
        Amount yooAmount = new Amount(amount.toPlainString(), "RUB");
        String description = "Posts autopay top-up";
        Receipt receipt = new Receipt(
                new Receipt.Customer(row.ownerEmail()),
                List.of(new Receipt.ReceiptItem(description, "1.00", yooAmount, yooKassaProperties.vatCode())));
        Map<String, String> metadata =
                Map.of("workspaceId", row.workspaceId().toString(), "purpose", "autopay", "autopay", "true");
        try {
            PaymentResponse response = yooKassaClient.createRecurringPayment(
                    yooAmount, row.providerPaymentMethodId(), description, metadata, receipt, idempotenceKey);
            UUID paymentId = UuidV7.generate();
            String status = response.status() == null ? "pending" : response.status();
            if ("succeeded".equals(status) || Boolean.TRUE.equals(response.paid())) {
                status = "succeeded";
            }
            jdbc.sql(
                            """
                            INSERT INTO payments (
                                id, workspace_id, provider, provider_payment_id, amount, amount_kopecks,
                                currency, status, purpose, metadata, idempotence_key, created_at, updated_at
                            ) VALUES (
                                :id, :workspaceId, 'yookassa', :providerPaymentId, :amount, :amountKopecks,
                                'RUB', :status, 'autopay', CAST(:metadata AS jsonb), :idempotenceKey, now(), now()
                            )
                            ON CONFLICT (provider_payment_id) DO NOTHING
                            """)
                    .param("id", paymentId)
                    .param("workspaceId", row.workspaceId())
                    .param("providerPaymentId", response.id())
                    .param("amount", amount)
                    .param("amountKopecks", row.topupAmountKopecks())
                    .param("status", status)
                    .param("metadata", "{\"purpose\":\"autopay\"}")
                    .param("idempotenceKey", idempotenceKey)
                    .update();
            jdbc.sql(
                            """
                            UPDATE autopay_settings
                            SET last_triggered_at = :now, last_payment_id = :paymentId, updated_at = now()
                            WHERE workspace_id = :workspaceId
                            """)
                    .param("now", Instant.now())
                    .param("paymentId", paymentId)
                    .param("workspaceId", row.workspaceId())
                    .update();
            // Wallet credit happens in API webhook processor on payment.succeeded
            return true;
        } catch (YooKassaException ex) {
            log.warn("Autopay failed for workspace {}: {}", row.workspaceId(), ex.getMessage());
            return false;
        }
    }

    private record AutopayRow(
            UUID workspaceId,
            long thresholdKopecks,
            long topupAmountKopecks,
            String providerPaymentMethodId,
            long balanceKopecks,
            String ownerEmail) {}
}
