package ru.posts.persistence.jdbc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.persistence.jdbc.WalletLedgerJdbc.InsufficientFundsException;

/**
 * Daily balance charges with uniqueness on (workspace_id, charge_date) and catch-up support.
 */
public class DailyBillingJdbc {

    public record ChargeOutcome(
            UUID workspaceId,
            LocalDate chargeDate,
            String status,
            long amountKopecks,
            UUID ledgerEntryId,
            boolean frozen) {}

    public record WorkspaceChargeCandidate(
            UUID workspaceId, UUID planId, long priceDayKopecks, String billingStatus, UUID ownerUserId) {}

    private final JdbcClient jdbc;
    private final WalletLedgerJdbc walletLedgerJdbc;

    public DailyBillingJdbc(JdbcClient jdbcClient, WalletLedgerJdbc walletLedgerJdbc) {
        this.jdbc = jdbcClient;
        this.walletLedgerJdbc = walletLedgerJdbc;
    }

    /**
     * Claims a batch of balance-mode workspaces that may need a charge for {@code chargeDate}
     * (missing daily_charges row). Uses SKIP LOCKED via advisory locks on workspace id.
     */
    @Transactional
    public List<WorkspaceChargeCandidate> claimCandidates(LocalDate chargeDate, int limit) {
        return jdbc.sql(
                        """
                        SELECT w.id AS workspace_id, w.plan_id, coalesce(p.price_day_kopecks, 0) AS price_day_kopecks,
                               w.billing_status, w.owner_user_id
                        FROM workspaces w
                        LEFT JOIN plans p ON p.id = w.plan_id
                        WHERE w.deleted_at IS NULL
                          AND w.billing_model = 'balance'
                          AND w.billing_status IN ('active', 'trial', 'grace')
                          AND NOT EXISTS (
                              SELECT 1 FROM daily_charges dc
                              WHERE dc.workspace_id = w.id AND dc.charge_date = :chargeDate
                          )
                        ORDER BY w.id
                        LIMIT :limit
                        FOR UPDATE OF w SKIP LOCKED
                        """)
                .param("chargeDate", chargeDate)
                .param("limit", limit)
                .query((rs, n) -> new WorkspaceChargeCandidate(
                        rs.getObject("workspace_id", UUID.class),
                        rs.getObject("plan_id", UUID.class),
                        rs.getLong("price_day_kopecks"),
                        rs.getString("billing_status"),
                        rs.getObject("owner_user_id", UUID.class)))
                .list();
    }

    @Transactional
    public ChargeOutcome chargeWorkspaceDay(
            WorkspaceChargeCandidate candidate, LocalDate chargeDate, int channelsCounted) {
        if (existsCharge(candidate.workspaceId(), chargeDate)) {
            return new ChargeOutcome(candidate.workspaceId(), chargeDate, "charged", 0, null, false);
        }
        long price = Math.max(0L, candidate.priceDayKopecks());
        if (price == 0L) {
            insertDailyCharge(
                    candidate.workspaceId(), chargeDate, 0, candidate.planId(), channelsCounted, null, "skipped_free");
            return new ChargeOutcome(candidate.workspaceId(), chargeDate, "skipped_free", 0, null, false);
        }

        String idempotencyKey = "daily:" + candidate.workspaceId() + ":" + chargeDate;
        try {
            var ledger = walletLedgerJdbc.debit(
                    candidate.workspaceId(), price, "charge", idempotencyKey, null, "Daily charge " + chargeDate);
            insertDailyCharge(
                    candidate.workspaceId(),
                    chargeDate,
                    price,
                    candidate.planId(),
                    channelsCounted,
                    ledger.entryId(),
                    "charged");
            return new ChargeOutcome(candidate.workspaceId(), chargeDate, "charged", price, ledger.entryId(), false);
        } catch (InsufficientFundsException ex) {
            insertDailyCharge(
                    candidate.workspaceId(),
                    chargeDate,
                    0,
                    candidate.planId(),
                    channelsCounted,
                    null,
                    "skipped_frozen");
            freezeWorkspace(candidate.workspaceId());
            return new ChargeOutcome(candidate.workspaceId(), chargeDate, "skipped_frozen", 0, null, true);
        } catch (DataIntegrityViolationException ex) {
            // unique (workspace, date) — another worker won
            return new ChargeOutcome(candidate.workspaceId(), chargeDate, "charged", 0, null, false);
        }
    }

    /**
     * Counts billable channels. A channel with {@code capabilities.duplicate=true} (vposter twin)
     * contributes weight 2; otherwise 1.
     */
    @Transactional(readOnly = true)
    public int countBillableChannels(UUID workspaceId) {
        Integer count = jdbc.sql(
                        """
                        SELECT coalesce(sum(
                            CASE
                              WHEN coalesce((c.capabilities->>'duplicate')::boolean, false) THEN 2
                              ELSE 1
                            END
                        ), 0)::int
                        FROM channels c
                        WHERE c.workspace_id = :workspaceId
                          AND c.deleted_at IS NULL
                          AND c.status <> 'disabled'
                        """)
                .param("workspaceId", workspaceId)
                .query(Integer.class)
                .optional()
                .orElse(0);
        return count == null ? 0 : count;
    }

    @Transactional
    public void freezeWorkspace(UUID workspaceId) {
        jdbc.sql(
                        """
                        UPDATE workspaces
                        SET billing_status = 'frozen', updated_at = now()
                        WHERE id = :id AND billing_status <> 'frozen'
                        """)
                .param("id", workspaceId)
                .update();
        jdbc.sql(
                        """
                        UPDATE posts
                        SET status = 'paused',
                            pause_reason = 'billing',
                            updated_at = now()
                        WHERE workspace_id = :workspaceId
                          AND deleted_at IS NULL
                          AND status IN ('scheduled', 'approved')
                        """)
                .param("workspaceId", workspaceId)
                .update();
    }

    @Transactional
    public void unfreezeWorkspace(UUID workspaceId) {
        jdbc.sql(
                        """
                        UPDATE workspaces
                        SET billing_status = 'active', updated_at = now()
                        WHERE id = :id AND billing_status = 'frozen'
                        """)
                .param("id", workspaceId)
                .update();
        jdbc.sql(
                        """
                        UPDATE posts
                        SET status = 'scheduled',
                            pause_reason = NULL,
                            updated_at = now()
                        WHERE workspace_id = :workspaceId
                          AND deleted_at IS NULL
                          AND status = 'paused'
                          AND pause_reason = 'billing'
                          AND scheduled_at IS NOT NULL
                        """)
                .param("workspaceId", workspaceId)
                .update();
    }

    @Transactional(readOnly = true)
    public boolean existsCharge(UUID workspaceId, LocalDate chargeDate) {
        Integer n = jdbc.sql(
                        """
                        SELECT 1 FROM daily_charges
                        WHERE workspace_id = :ws AND charge_date = :d
                        LIMIT 1
                        """)
                .param("ws", workspaceId)
                .param("d", chargeDate)
                .query(Integer.class)
                .optional()
                .orElse(null);
        return n != null;
    }

    @Transactional(readOnly = true)
    public LocalDate lastChargeDate(UUID workspaceId) {
        return jdbc.sql(
                        """
                        SELECT max(charge_date) FROM daily_charges WHERE workspace_id = :ws
                        """)
                .param("ws", workspaceId)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }

    private UUID insertDailyCharge(
            UUID workspaceId,
            LocalDate chargeDate,
            long amountKopecks,
            UUID planId,
            int channelsCounted,
            UUID ledgerEntryId,
            String status) {
        UUID id = UuidV7.generate();
        jdbc.sql(
                        """
                        INSERT INTO daily_charges (
                            id, workspace_id, charge_date, amount_kopecks, plan_id,
                            channels_counted, ledger_entry_id, status, created_at
                        ) VALUES (
                            :id, :workspaceId, :chargeDate, :amount, :planId,
                            :channels, :ledgerId, :status, now()
                        )
                        """)
                .param("id", id)
                .param("workspaceId", workspaceId)
                .param("chargeDate", chargeDate)
                .param("amount", amountKopecks)
                .param("planId", planId)
                .param("channels", channelsCounted)
                .param("ledgerId", ledgerEntryId)
                .param("status", status)
                .update();
        return id;
    }

    /** Appends outbox notify event for billing (relayed by scheduler). */
    @Transactional
    public void notifyBilling(UUID workspaceId, String eventType, Map<String, Object> extraPayload) {
        // payload built as JSON string by caller via ObjectMapper — keep simple map serialization here
        StringBuilder json =
                new StringBuilder("{\"workspaceId\":\"").append(workspaceId).append("\"");
        if (extraPayload != null) {
            for (var e : extraPayload.entrySet()) {
                json.append(",\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v == null) {
                    json.append("null");
                } else if (v instanceof Number || v instanceof Boolean) {
                    json.append(v);
                } else {
                    json.append("\"").append(v.toString().replace("\"", "\\\"")).append("\"");
                }
            }
        }
        json.append("}");
        jdbc.sql(
                        """
                        INSERT INTO outbox (aggregate, aggregate_id, event_type, payload)
                        VALUES ('workspace', :aggregateId, :eventType, CAST(:payload AS jsonb))
                        """)
                .param("aggregateId", workspaceId)
                .param("eventType", eventType)
                .param("payload", json.toString())
                .update();
    }
}
