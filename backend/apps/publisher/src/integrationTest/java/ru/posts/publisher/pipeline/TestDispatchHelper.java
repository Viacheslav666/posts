package ru.posts.publisher.pipeline;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.messaging.publish.ActionCommand;
import ru.posts.messaging.publish.ModerationActionCommand;
import ru.posts.messaging.publish.PublishCommand;
import ru.posts.messaging.topology.ActionsTopology;
import ru.posts.messaging.topology.ModerationActionsTopology;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.messaging.trace.AmqpTraceHeaders;
import ru.posts.persistence.jdbc.PublicationDispatchRow;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;
import ru.posts.persistence.jdbc.ScheduledActionDispatchRow;
import ru.posts.persistence.jdbc.ScheduledActionDispatcherJdbc;
import ru.posts.persistence.jdbc.ScheduledActionLifecycleJdbc;
import ru.posts.persistence.jdbc.SocialRemoteActionDispatchRow;
import ru.posts.persistence.jdbc.SocialRemoteActionDispatcherJdbc;
import ru.posts.persistence.jdbc.SocialRemoteActionLifecycleJdbc;

/** Test-only dispatcher mirroring posts-scheduler (avoids app→app dependency). */
@Component
public class TestDispatchHelper {

    private final PublicationDispatcherJdbc dispatcherJdbc;
    private final PublicationLifecycleJdbc lifecycleJdbc;
    private final ScheduledActionDispatcherJdbc actionDispatcherJdbc;
    private final ScheduledActionLifecycleJdbc actionLifecycleJdbc;
    private final SocialRemoteActionDispatcherJdbc remoteActionDispatcherJdbc;
    private final SocialRemoteActionLifecycleJdbc remoteActionLifecycleJdbc;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    public TestDispatchHelper(
            PublicationDispatcherJdbc dispatcherJdbc,
            PublicationLifecycleJdbc lifecycleJdbc,
            ScheduledActionDispatcherJdbc actionDispatcherJdbc,
            ScheduledActionLifecycleJdbc actionLifecycleJdbc,
            SocialRemoteActionDispatcherJdbc remoteActionDispatcherJdbc,
            SocialRemoteActionLifecycleJdbc remoteActionLifecycleJdbc,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate) {
        this.dispatcherJdbc = dispatcherJdbc;
        this.lifecycleJdbc = lifecycleJdbc;
        this.actionDispatcherJdbc = actionDispatcherJdbc;
        this.actionLifecycleJdbc = actionLifecycleJdbc;
        this.remoteActionDispatcherJdbc = remoteActionDispatcherJdbc;
        this.remoteActionLifecycleJdbc = remoteActionLifecycleJdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public int dispatchDueBatch() {
        List<PublicationDispatchRow> claimed = transactionTemplate.execute(
                status -> dispatcherJdbc.claimDue(1000, Duration.ofMinutes(10), Duration.ofSeconds(5)));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        List<CorrelationData> correlations = new ArrayList<>(claimed.size());
        for (PublicationDispatchRow row : claimed) {
            CorrelationData correlation = new CorrelationData(row.id().toString());
            String trace = AmqpTraceHeaders.currentOrNew(null);
            rabbitTemplate.convertAndSend(
                    PublishTopology.PUBLISH_EXCHANGE,
                    row.network(),
                    new PublishCommand(row.id(), trace),
                    AmqpTraceHeaders.withTraceparent(trace),
                    correlation);
            correlations.add(correlation);
        }

        List<UUID> failed = new ArrayList<>();
        for (int i = 0; i < correlations.size(); i++) {
            try {
                CorrelationData.Confirm confirm =
                        correlations.get(i).getFuture().get(5, TimeUnit.SECONDS);
                if (confirm == null || !confirm.isAck()) {
                    failed.add(claimed.get(i).id());
                }
            } catch (Exception ex) {
                failed.add(claimed.get(i).id());
            }
        }
        if (!failed.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.revertQueuedToPending(failed));
        }
        return claimed.size() - failed.size();
    }

    public int dispatchDueActions() {
        List<ScheduledActionDispatchRow> claimed = transactionTemplate.execute(
                status -> actionDispatcherJdbc.claimDue(1000, Duration.ofMinutes(10), Duration.ofSeconds(5)));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        List<CorrelationData> correlations = new ArrayList<>(claimed.size());
        for (ScheduledActionDispatchRow row : claimed) {
            CorrelationData correlation = new CorrelationData(row.id().toString());
            String network = row.network() == null ? "tg" : row.network();
            String trace = AmqpTraceHeaders.currentOrNew(null);
            rabbitTemplate.convertAndSend(
                    ActionsTopology.ACTIONS_EXCHANGE,
                    network,
                    new ActionCommand(row.id(), trace),
                    AmqpTraceHeaders.withTraceparent(trace),
                    correlation);
            correlations.add(correlation);
        }
        List<UUID> failed = new ArrayList<>();
        for (int i = 0; i < correlations.size(); i++) {
            try {
                CorrelationData.Confirm confirm =
                        correlations.get(i).getFuture().get(5, TimeUnit.SECONDS);
                if (confirm == null || !confirm.isAck()) {
                    failed.add(claimed.get(i).id());
                }
            } catch (Exception ex) {
                failed.add(claimed.get(i).id());
            }
        }
        if (!failed.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> actionLifecycleJdbc.revertQueuedToPending(failed));
        }
        return claimed.size() - failed.size();
    }

    /** Dispatches due {@code social_remote_actions} to {@code actions.moderation.<network>} (BE-33). */
    public int dispatchDueModerationActions() {
        List<SocialRemoteActionDispatchRow> claimed = transactionTemplate.execute(
                status -> remoteActionDispatcherJdbc.claimDue(1000, Duration.ofMinutes(10), Duration.ofSeconds(5)));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }
        List<CorrelationData> correlations = new ArrayList<>(claimed.size());
        for (SocialRemoteActionDispatchRow row : claimed) {
            CorrelationData correlation = new CorrelationData(row.id().toString());
            String network = row.network() == null ? "tg" : row.network();
            String trace = AmqpTraceHeaders.currentOrNew(null);
            rabbitTemplate.convertAndSend(
                    ModerationActionsTopology.MODERATION_ACTIONS_EXCHANGE,
                    network,
                    new ModerationActionCommand(row.id(), trace),
                    AmqpTraceHeaders.withTraceparent(trace),
                    correlation);
            correlations.add(correlation);
        }
        List<UUID> failed = new ArrayList<>();
        for (int i = 0; i < correlations.size(); i++) {
            try {
                CorrelationData.Confirm confirm =
                        correlations.get(i).getFuture().get(5, TimeUnit.SECONDS);
                if (confirm == null || !confirm.isAck()) {
                    failed.add(claimed.get(i).id());
                }
            } catch (Exception ex) {
                failed.add(claimed.get(i).id());
            }
        }
        if (!failed.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> remoteActionLifecycleJdbc.revertQueuedToPending(failed));
        }
        return claimed.size() - failed.size();
    }

    public int reapStuck() {
        Integer pubs = transactionTemplate.execute(status -> lifecycleJdbc.reaperStuck());
        Integer actions = transactionTemplate.execute(status -> actionLifecycleJdbc.reaperStuck());
        Integer remote = transactionTemplate.execute(status -> remoteActionLifecycleJdbc.reaperStuck());
        return (pubs == null ? 0 : pubs) + (actions == null ? 0 : actions) + (remote == null ? 0 : remote);
    }
}
