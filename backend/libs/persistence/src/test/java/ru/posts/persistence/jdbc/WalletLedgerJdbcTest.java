package ru.posts.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletLedgerJdbcTest {

    @Test
    void insufficientFundsExceptionCarriesAmounts() {
        UUID ws = UUID.randomUUID();
        var ex = new WalletLedgerJdbc.InsufficientFundsException(ws, 100, 500);
        assertThat(ex.workspaceId()).isEqualTo(ws);
        assertThat(ex.balance()).isEqualTo(100);
        assertThat(ex.required()).isEqualTo(500);
    }

    @Test
    void walletSnapshotRefundable() {
        var snap = new WalletLedgerJdbc.WalletSnapshot(UUID.randomUUID(), 10_000, 3_000, 1);
        assertThat(snap.refundableKopecks()).isEqualTo(7_000);
    }
}
