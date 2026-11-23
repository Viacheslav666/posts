package ru.posts.persistence.jdbc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;

/**
 * Append-only wallet ledger (BE-31). Invariant: {@code wallets.balance_kopecks = SUM(ledger.amount_kopecks)}.
 */
public class WalletLedgerJdbc {

    public record WalletSnapshot(UUID workspaceId, long balanceKopecks, long bonusBalanceKopecks, long version) {
        public long refundableKopecks() {
            return Math.max(0L, balanceKopecks - bonusBalanceKopecks);
        }
    }

    public record LedgerResult(UUID entryId, long amountKopecks, boolean replayed) {}

    /**
     * Outcome of a debit that must not mark the caller's transaction rollback-only. {@code charged}
     * is false when the wallet cannot cover the amount.
     */
    public record DebitOutcome(
            boolean charged, LedgerResult result, UUID workspaceId, long balanceKopecks, long requiredKopecks) {
        public static DebitOutcome charged(LedgerResult result) {
            return new DebitOutcome(true, result, null, 0L, 0L);
        }

        public static DebitOutcome insufficient(UUID workspaceId, long balanceKopecks, long requiredKopecks) {
            return new DebitOutcome(false, null, workspaceId, balanceKopecks, requiredKopecks);
        }
    }

    private final JdbcClient jdbc;

    public WalletLedgerJdbc(JdbcClient jdbcClient) {
        this.jdbc = jdbcClient;
    }

    @Transactional
    public WalletSnapshot lockOrCreate(UUID workspaceId) {
        Optional<WalletSnapshot> existing = jdbc.sql(
                        """
                        SELECT workspace_id, balance_kopecks, bonus_balance_kopecks, version
                        FROM wallets WHERE workspace_id = :id FOR UPDATE
                        """)
                .param("id", workspaceId)
                .query((rs, n) -> new WalletSnapshot(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getLong("balance_kopecks"),
                        rs.getLong("bonus_balance_kopecks"),
                        rs.getLong("version")))
                .optional();
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.sql(
                            """
                            INSERT INTO wallets (workspace_id, balance_kopecks, bonus_balance_kopecks, version, updated_at)
                            VALUES (:id, 0, 0, 0, now())
                            """)
                    .param("id", workspaceId)
                    .update();
        } catch (DataIntegrityViolationException ignored) {
            // concurrent create
        }
        return jdbc.sql(
                        """
                        SELECT workspace_id, balance_kopecks, bonus_balance_kopecks, version
                        FROM wallets WHERE workspace_id = :id FOR UPDATE
                        """)
                .param("id", workspaceId)
                .query((rs, n) -> new WalletSnapshot(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getLong("balance_kopecks"),
                        rs.getLong("bonus_balance_kopecks"),
                        rs.getLong("version")))
                .single();
    }

    @Transactional(readOnly = true)
    public Optional<WalletSnapshot> find(UUID workspaceId) {
        return jdbc.sql(
                        """
                        SELECT workspace_id, balance_kopecks, bonus_balance_kopecks, version
                        FROM wallets WHERE workspace_id = :id
                        """)
                .param("id", workspaceId)
                .query((rs, n) -> new WalletSnapshot(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getLong("balance_kopecks"),
                        rs.getLong("bonus_balance_kopecks"),
                        rs.getLong("version")))
                .optional();
    }

    @Transactional
    public LedgerResult credit(
            UUID workspaceId,
            long amountKopecks,
            String kind,
            boolean bonus,
            String idempotencyKey,
            UUID refId,
            String description) {
        if (amountKopecks <= 0) {
            throw new IllegalArgumentException("credit amount must be > 0");
        }
        Optional<UUID> replay = findByIdempotency(idempotencyKey);
        if (replay.isPresent()) {
            return new LedgerResult(replay.get(), amountKopecks, true);
        }
        lockOrCreate(workspaceId);
        replay = findByIdempotency(idempotencyKey);
        if (replay.isPresent()) {
            return new LedgerResult(replay.get(), amountKopecks, true);
        }
        UUID entryId = UuidV7.generate();
        try {
            insertLedger(entryId, workspaceId, amountKopecks, kind, bonus, idempotencyKey, refId, description);
        } catch (IdempotentReplayException ex) {
            return new LedgerResult(ex.entryId(), amountKopecks, true);
        }
        jdbc.sql(
                        """
                        UPDATE wallets
                        SET balance_kopecks = balance_kopecks + :amount,
                            bonus_balance_kopecks = bonus_balance_kopecks + :bonusAmount,
                            version = version + 1,
                            updated_at = now()
                        WHERE workspace_id = :id
                        """)
                .param("amount", amountKopecks)
                .param("bonusAmount", bonus ? amountKopecks : 0L)
                .param("id", workspaceId)
                .update();
        assertInvariant(workspaceId);
        return new LedgerResult(entryId, amountKopecks, false);
    }

    @Transactional
    public LedgerResult debit(
            UUID workspaceId, long amountKopecks, String kind, String idempotencyKey, UUID refId, String description) {
        DebitOutcome outcome = applyDebit(workspaceId, amountKopecks, kind, idempotencyKey, refId, description);
        if (!outcome.charged()) {
            throw new InsufficientFundsException(
                    outcome.workspaceId(), outcome.balanceKopecks(), outcome.requiredKopecks());
        }
        return outcome.result();
    }

    /**
     * Debits when the balance covers the amount. A short balance is returned, not thrown, so a
     * joined transaction (grabber publish) is not marked rollback-only.
     */
    @Transactional
    public DebitOutcome tryDebit(
            UUID workspaceId, long amountKopecks, String kind, String idempotencyKey, UUID refId, String description) {
        return applyDebit(workspaceId, amountKopecks, kind, idempotencyKey, refId, description);
    }

    private DebitOutcome applyDebit(
            UUID workspaceId, long amountKopecks, String kind, String idempotencyKey, UUID refId, String description) {
        if (amountKopecks <= 0) {
            throw new IllegalArgumentException("debit amount must be > 0");
        }
        Optional<UUID> replay = findByIdempotency(idempotencyKey);
        if (replay.isPresent()) {
            return DebitOutcome.charged(new LedgerResult(replay.get(), -amountKopecks, true));
        }
        WalletSnapshot wallet = lockOrCreate(workspaceId);
        replay = findByIdempotency(idempotencyKey);
        if (replay.isPresent()) {
            return DebitOutcome.charged(new LedgerResult(replay.get(), -amountKopecks, true));
        }
        if (wallet.balanceKopecks() < amountKopecks) {
            return DebitOutcome.insufficient(workspaceId, wallet.balanceKopecks(), amountKopecks);
        }
        long bonusSpend = Math.min(wallet.bonusBalanceKopecks(), amountKopecks);
        UUID entryId = UuidV7.generate();
        try {
            insertLedger(entryId, workspaceId, -amountKopecks, kind, false, idempotencyKey, refId, description);
        } catch (IdempotentReplayException ex) {
            return DebitOutcome.charged(new LedgerResult(ex.entryId(), -amountKopecks, true));
        }
        jdbc.sql(
                        """
                        UPDATE wallets
                        SET balance_kopecks = balance_kopecks - :amount,
                            bonus_balance_kopecks = bonus_balance_kopecks - :bonusSpend,
                            version = version + 1,
                            updated_at = now()
                        WHERE workspace_id = :id
                        """)
                .param("amount", amountKopecks)
                .param("bonusSpend", bonusSpend)
                .param("id", workspaceId)
                .update();
        assertInvariant(workspaceId);
        return DebitOutcome.charged(new LedgerResult(entryId, -amountKopecks, false));
    }

    @Transactional(readOnly = true)
    public long sumLedger(UUID workspaceId) {
        Long sum = jdbc.sql("SELECT coalesce(sum(amount_kopecks), 0) FROM ledger_entries WHERE workspace_id = :id")
                .param("id", workspaceId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return sum == null ? 0L : sum;
    }

    @Transactional(readOnly = true)
    public void assertInvariant(UUID workspaceId) {
        WalletSnapshot wallet = find(workspaceId).orElse(null);
        if (wallet == null) {
            return;
        }
        long sum = sumLedger(workspaceId);
        if (sum != wallet.balanceKopecks()) {
            throw new IllegalStateException("Wallet invariant broken for "
                    + workspaceId
                    + ": cached="
                    + wallet.balanceKopecks()
                    + " sum="
                    + sum);
        }
    }

    private Optional<UUID> findByIdempotency(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id FROM ledger_entries WHERE idempotency_key = :key")
                .param("key", key)
                .query((rs, n) -> rs.getObject("id", UUID.class))
                .optional();
    }

    private void insertLedger(
            UUID entryId,
            UUID workspaceId,
            long amountKopecks,
            String kind,
            boolean bonus,
            String idempotencyKey,
            UUID refId,
            String description) {
        BigDecimal amount = BigDecimal.valueOf(amountKopecks, 2).setScale(2, RoundingMode.HALF_UP);
        try {
            jdbc.sql(
                            """
                            INSERT INTO ledger_entries (
                                id, workspace_id, amount, amount_kopecks, kind, is_bonus,
                                idempotency_key, currency, ref_id, description, created_at
                            ) VALUES (
                                :id, :workspaceId, :amount, :amountKopecks, :kind, :isBonus,
                                :idempotencyKey, 'RUB', :refId, :description, :createdAt
                            )
                            """)
                    .param("id", entryId)
                    .param("workspaceId", workspaceId)
                    .param("amount", amount)
                    .param("amountKopecks", amountKopecks)
                    .param("kind", kind)
                    .param("isBonus", bonus)
                    .param("idempotencyKey", idempotencyKey)
                    .param("refId", refId, Types.OTHER)
                    .param("description", description)
                    .param("createdAt", Timestamp.from(Instant.now()))
                    .update();
        } catch (DataIntegrityViolationException ex) {
            Optional<UUID> existing = findByIdempotency(idempotencyKey);
            if (existing.isPresent()) {
                throw new IdempotentReplayException(existing.get());
            }
            throw ex;
        }
    }

    public static final class InsufficientFundsException extends RuntimeException {
        private final UUID workspaceId;
        private final long balance;
        private final long required;

        public InsufficientFundsException(UUID workspaceId, long balance, long required) {
            super("Insufficient funds workspace=" + workspaceId + " balance=" + balance + " required=" + required);
            this.workspaceId = workspaceId;
            this.balance = balance;
            this.required = required;
        }

        public UUID workspaceId() {
            return workspaceId;
        }

        public long balance() {
            return balance;
        }

        public long required() {
            return required;
        }
    }

    public static final class IdempotentReplayException extends RuntimeException {
        private final UUID entryId;

        public IdempotentReplayException(UUID entryId) {
            super("ledger idempotent replay " + entryId);
            this.entryId = entryId;
        }

        public UUID entryId() {
            return entryId;
        }
    }
}
