package ru.posts.scheduler.billing;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.persistence.jdbc.DailyBillingJdbc;
import ru.posts.persistence.jdbc.DailyBillingJdbc.ChargeOutcome;
import ru.posts.persistence.jdbc.DailyBillingJdbc.WorkspaceChargeCandidate;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;

/**
 * Daily balance charges for up to ~300k workspaces (BE-31).
 *
 * <p>ShedLock ensures a single leader; each batch uses {@code FOR UPDATE SKIP LOCKED}. Catch-up
 * charges missing days from last charge (or yesterday window) until today (MSK).
 */
@Component
public class BillingDailyChargeScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingDailyChargeScheduler.class);
    private static final ZoneId MSK = ZoneId.of("Europe/Moscow");

    private final DailyBillingJdbc dailyBillingJdbc;
    private final WalletLedgerJdbc walletLedgerJdbc;
    private final TransactionTemplate tx;
    private final int batchSize;
    private final int maxCatchUpDays;
    private final int lowBalanceDays;

    public BillingDailyChargeScheduler(
            DailyBillingJdbc dailyBillingJdbc,
            WalletLedgerJdbc walletLedgerJdbc,
            TransactionTemplate tx,
            @Value("${posts.billing.daily-charge-batch-size:500}") int batchSize,
            @Value("${posts.billing.max-catch-up-days:14}") int maxCatchUpDays,
            @Value("${posts.billing.low-balance-days-threshold:3}") int lowBalanceDays) {
        this.dailyBillingJdbc = dailyBillingJdbc;
        this.walletLedgerJdbc = walletLedgerJdbc;
        this.tx = tx;
        this.batchSize = batchSize;
        this.maxCatchUpDays = maxCatchUpDays;
        this.lowBalanceDays = lowBalanceDays;
    }

    @Scheduled(cron = "${posts.billing.daily-charge.cron:0 5 3 * * *}", zone = "Europe/Moscow")
    @SchedulerLock(name = "billingDailyCharge", lockAtMostFor = "2h", lockAtLeastFor = "1m")
    public void runDaily() {
        LocalDate today = LocalDate.now(MSK);
        int charged = 0;
        int frozen = 0;
        int batches = 0;
        while (true) {
            List<WorkspaceChargeCandidate> batch =
                    tx.execute(status -> dailyBillingJdbc.claimCandidates(today, batchSize));
            if (batch == null || batch.isEmpty()) {
                break;
            }
            batches++;
            for (WorkspaceChargeCandidate candidate : batch) {
                ChargeOutcome outcome = tx.execute(status -> processWorkspace(candidate, today));
                if (outcome == null) {
                    continue;
                }
                if ("charged".equals(outcome.status()) || "skipped_free".equals(outcome.status())) {
                    charged++;
                }
                if (outcome.frozen()) {
                    frozen++;
                }
            }
            if (batch.size() < batchSize) {
                break;
            }
            // safety: avoid infinite loops if SKIP LOCKED keeps returning same set
            if (batches > 10_000) {
                log.warn("Daily charge aborted after {} batches", batches);
                break;
            }
        }
        log.info("Billing daily charge done: batches={}, chargedOrSkipped={}, frozen={}", batches, charged, frozen);
    }

    private ChargeOutcome processWorkspace(WorkspaceChargeCandidate candidate, LocalDate today) {
        LocalDate last = dailyBillingJdbc.lastChargeDate(candidate.workspaceId());
        LocalDate from = last == null ? today.minusDays(Math.min(maxCatchUpDays, 1)) : last.plusDays(1);
        if (from.isBefore(today.minusDays(maxCatchUpDays))) {
            from = today.minusDays(maxCatchUpDays);
        }
        ChargeOutcome lastOutcome = null;
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            if (dailyBillingJdbc.existsCharge(candidate.workspaceId(), day)) {
                continue;
            }
            int channels = dailyBillingJdbc.countBillableChannels(candidate.workspaceId());
            lastOutcome = dailyBillingJdbc.chargeWorkspaceDay(candidate, day, channels);
            if (lastOutcome.frozen()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put(
                        "balanceKopecks",
                        walletLedgerJdbc
                                .find(candidate.workspaceId())
                                .map(WalletLedgerJdbc.WalletSnapshot::balanceKopecks)
                                .orElse(0L));
                dailyBillingJdbc.notifyBilling(candidate.workspaceId(), "billing.access_suspended", payload);
                break;
            }
            maybeNotifyLowBalance(candidate);
        }
        return lastOutcome;
    }

    private void maybeNotifyLowBalance(WorkspaceChargeCandidate candidate) {
        if (candidate.priceDayKopecks() <= 0) {
            return;
        }
        var wallet = walletLedgerJdbc.find(candidate.workspaceId()).orElse(null);
        if (wallet == null) {
            return;
        }
        long days = wallet.balanceKopecks() / candidate.priceDayKopecks();
        if (days <= lowBalanceDays) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("balanceKopecks", wallet.balanceKopecks());
            payload.put("daysRemaining", days);
            dailyBillingJdbc.notifyBilling(candidate.workspaceId(), "billing.low_balance", payload);
        }
    }
}
