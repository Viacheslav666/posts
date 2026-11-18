package ru.posts.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.DailyBillingJdbc;
import ru.posts.persistence.jdbc.DailyBillingJdbc.WorkspaceChargeCandidate;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;
import ru.posts.persistence.jdbc.WalletLedgerJdbc.InsufficientFundsException;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WalletBalanceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("posts")
            .withUsername("posts")
            .withPassword("posts");

    @Autowired
    WalletLedgerJdbc walletLedgerJdbc;

    @Autowired
    DailyBillingJdbc dailyBillingJdbc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    @DisplayName("wallet invariant: balance equals sum of ledger")
    void balanceInvariant() {
        UUID workspaceId = seedWorkspace();
        tx.executeWithoutResult(s -> {
            walletLedgerJdbc.credit(workspaceId, 10_000, "payment", false, "inv-c1-" + workspaceId, null, "topup");
            walletLedgerJdbc.credit(workspaceId, 2_000, "promo", true, "inv-c2-" + workspaceId, null, "bonus");
            walletLedgerJdbc.debit(workspaceId, 3_000, "charge", "inv-d1-" + workspaceId, null, "day");
        });
        var wallet = walletLedgerJdbc.find(workspaceId).orElseThrow();
        assertThat(wallet.balanceKopecks()).isEqualTo(9_000);
        assertThat(wallet.bonusBalanceKopecks()).isEqualTo(0); // bonus spent first (2000) + 1000 from real
        // Actually bonus 2000 spent first, then 1000 from real → bonus=0, balance=9000, refundable=9000
        assertThat(wallet.refundableKopecks()).isEqualTo(9_000);
        walletLedgerJdbc.assertInvariant(workspaceId);
        assertThat(walletLedgerJdbc.sumLedger(workspaceId)).isEqualTo(wallet.balanceKopecks());
    }

    @Test
    @DisplayName("concurrent daily charges: exactly one row per workspace/day")
    void concurrentDailyChargeOnce() throws Exception {
        UUID workspaceId = seedWorkspace();
        UUID planId = jdbc.sql("SELECT id FROM plans WHERE code = 'daily_6' LIMIT 1")
                .query(UUID.class)
                .single();
        jdbc.sql(
                        "UPDATE workspaces SET plan_id = :planId, billing_model = 'balance', billing_status = 'active' WHERE id = :id")
                .param("planId", planId)
                .param("id", workspaceId)
                .update();
        tx.executeWithoutResult(s -> walletLedgerJdbc.credit(
                workspaceId, 100_000, "payment", false, "conc-credit-" + workspaceId, null, "seed"));

        var candidate = new WorkspaceChargeCandidate(workspaceId, planId, 1300L, "active", null);
        var day = java.time.LocalDate.of(2026, 11, 18);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    tx.executeWithoutResult(s -> dailyBillingJdbc.chargeWorkspaceDay(candidate, day, 1));
                    successes.incrementAndGet();
                } catch (Exception ignored) {
                    // unique violation / race — expected for losers
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Integer count = jdbc.sql("SELECT count(*) FROM daily_charges WHERE workspace_id = :ws AND charge_date = :d")
                .param("ws", workspaceId)
                .param("d", day)
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);

        Long chargeLedger = jdbc.sql(
                        """
                        SELECT count(*) FROM ledger_entries
                        WHERE workspace_id = :ws AND idempotency_key = :k
                        """)
                .param("ws", workspaceId)
                .param("k", "daily:" + workspaceId + ":" + day)
                .query(Long.class)
                .single();
        assertThat(chargeLedger).isEqualTo(1L);
        walletLedgerJdbc.assertInvariant(workspaceId);
    }

    @Test
    @DisplayName("debit fails when insufficient funds")
    void insufficientFunds() {
        UUID workspaceId = seedWorkspace();
        assertThatThrownBy(() -> tx.executeWithoutResult(
                        s -> walletLedgerJdbc.debit(workspaceId, 100, "charge", "isf-" + workspaceId, null, "x")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    private UUID seedWorkspace() {
        UUID userId = UuidV7.generate();
        UUID workspaceId = UuidV7.generate();
        UUID freePlan = jdbc.sql("SELECT id FROM plans WHERE code = 'free' LIMIT 1")
                .query(UUID.class)
                .single();
        jdbc.sql(
                        """
                        INSERT INTO users (id, email, name, password_hash, created_at, updated_at)
                        VALUES (:id, :email, 'T', 'x', now(), now())
                        """)
                .param("id", userId)
                .param("email", "wallet-" + userId + "@example.com")
                .update();
        jdbc.sql(
                        """
                        INSERT INTO workspaces (id, name, owner_user_id, plan_id, billing_status, billing_model, created_at, updated_at)
                        VALUES (:id, 'W', :owner, :planId, 'active', 'balance', now(), now())
                        """)
                .param("id", workspaceId)
                .param("owner", userId)
                .param("planId", freePlan)
                .update();
        return workspaceId;
    }
}
