package ru.posts.api.billing;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pauses due scheduled/approved posts when a workspace is frozen for billing non-payment.
 *
 * <p>No dedicated {@code pause_reason} column yet — freeze flips status to {@code paused}; unfreeze
 * restores posts that still have {@code scheduled_at} back to {@code scheduled}. Manual user pauses
 * without a schedule are left as {@code paused} (publisher should also gate on
 * {@code workspaces.billing_status}).
 */
@Service
@Profile("!openapi")
public class BillingFreezeService {

    private final JdbcClient jdbcClient;

    public BillingFreezeService(javax.sql.DataSource dataSource) {
        this.jdbcClient = JdbcClient.create(dataSource);
    }

    @Transactional
    public void freezePublications(UUID workspaceId) {
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

    @Transactional
    public void unfreeze(UUID workspaceId) {
        // TODO(publisher): also skip dispatch while billing_status=frozen
        jdbcClient
                .sql(
                        """
                        UPDATE posts
                        SET status = 'scheduled', updated_at = now()
                        WHERE workspace_id = :workspaceId
                          AND deleted_at IS NULL
                          AND status = 'paused'
                          AND scheduled_at IS NOT NULL
                        """)
                .param("workspaceId", workspaceId)
                .update();
    }
}
