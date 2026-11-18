package ru.posts.api.billing;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.jdbc.DailyBillingJdbc;

/**
 * Pauses scheduled/approved posts when a workspace is frozen for billing.
 *
 * <p>Uses {@code posts.pause_reason='billing'} so unfreeze restores only billing pauses, not manual
 * user pauses.
 */
@Service
@Profile("!openapi")
public class BillingFreezeService {

    private final DailyBillingJdbc dailyBillingJdbc;

    public BillingFreezeService(DailyBillingJdbc dailyBillingJdbc) {
        this.dailyBillingJdbc = dailyBillingJdbc;
    }

    @Transactional
    public void freezePublications(UUID workspaceId) {
        dailyBillingJdbc.freezeWorkspace(workspaceId);
    }

    @Transactional
    public void unfreeze(UUID workspaceId) {
        dailyBillingJdbc.unfreezeWorkspace(workspaceId);
    }
}
