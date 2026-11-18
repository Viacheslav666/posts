package ru.posts.api.billing;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.persistence.jdbc.WalletLedgerJdbc;
import ru.posts.persistence.jdbc.WalletLedgerJdbc.InsufficientFundsException;
import ru.posts.persistence.jdbc.WalletLedgerJdbc.LedgerResult;
import ru.posts.persistence.jdbc.WalletLedgerJdbc.WalletSnapshot;

/** API facade over {@link WalletLedgerJdbc}. */
@Service
@Profile("!openapi")
public class WalletLedgerService {

    private final WalletLedgerJdbc ledgerJdbc;

    public WalletLedgerService(WalletLedgerJdbc ledgerJdbc) {
        this.ledgerJdbc = ledgerJdbc;
    }

    @Transactional
    public WalletSnapshot requireWalletForUpdate(UUID workspaceId) {
        return ledgerJdbc.lockOrCreate(workspaceId);
    }

    @Transactional
    public WalletSnapshot getOrCreate(UUID workspaceId) {
        return ledgerJdbc.find(workspaceId).orElseGet(() -> ledgerJdbc.lockOrCreate(workspaceId));
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
        try {
            return ledgerJdbc.credit(workspaceId, amountKopecks, kind, bonus, idempotencyKey, refId, description);
        } catch (WalletLedgerJdbc.IdempotentReplayException ex) {
            return new LedgerResult(ex.entryId(), amountKopecks, true);
        }
    }

    @Transactional
    public LedgerResult debit(
            UUID workspaceId, long amountKopecks, String kind, String idempotencyKey, UUID refId, String description) {
        try {
            return ledgerJdbc.debit(workspaceId, amountKopecks, kind, idempotencyKey, refId, description);
        } catch (InsufficientFundsException ex) {
            throw new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    ProblemCodes.BILLING_INSUFFICIENT_FUNDS,
                    "Insufficient wallet balance");
        } catch (WalletLedgerJdbc.IdempotentReplayException ex) {
            return new LedgerResult(ex.entryId(), -amountKopecks, true);
        }
    }

    @Transactional(readOnly = true)
    public void assertInvariant(UUID workspaceId) {
        ledgerJdbc.assertInvariant(workspaceId);
    }

    @Transactional(readOnly = true)
    public long sumLedger(UUID workspaceId) {
        return ledgerJdbc.sumLedger(workspaceId);
    }
}
