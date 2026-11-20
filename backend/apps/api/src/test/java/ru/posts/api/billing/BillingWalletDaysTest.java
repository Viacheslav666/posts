package ru.posts.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.entity.WorkspaceEntity;

class BillingWalletDaysTest {

    @Test
    void trialStatusUsesTrialEndsAtNotBalance() {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setBillingStatus("trial");
        workspace.setTrialEndsAt(Instant.now().plus(5, ChronoUnit.DAYS));

        Integer days = BillingServiceImpl.resolveDaysRemaining(workspace, 0L, 2700L);
        assertThat(days).isGreaterThanOrEqualTo(4);
    }

    @Test
    void activeUsesBalanceOverDailyPrice() {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setBillingStatus("active");

        Integer days = BillingServiceImpl.resolveDaysRemaining(workspace, 5400L, 2700L);
        assertThat(days).isEqualTo(2);
    }
}
