package ru.posts.scheduler.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
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
import ru.posts.scheduler.config.SchedulerProperties;
import ru.posts.scheduler.metrics.SchedulerMetrics;

@Service
public class PublicationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PublicationDispatchService.class);

    private final PublicationDispatcherJdbc dispatcherJdbc;
    private final PublicationLifecycleJdbc lifecycleJdbc;
    private final ScheduledActionDispatcherJdbc actionDispatcherJdbc;
    private final ScheduledActionLifecycleJdbc actionLifecycleJdbc;
    private final SocialRemoteActionDispatcherJdbc remoteActionDispatcherJdbc;
    private final SocialRemoteActionLifecycleJdbc remoteActionLifecycleJdbc;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SchedulerProperties properties;
    private final SchedulerMetrics metrics;
    private final AtomicLong lastDispatchEpochMs = new AtomicLong(System.currentTimeMillis());

    public PublicationDispatchService(
            PublicationDispatcherJdbc dispatcherJdbc,
            PublicationLifecycleJdbc lifecycleJdbc,
            ScheduledActionDispatcherJdbc actionDispatcherJdbc,
            ScheduledActionLifecycleJdbc actionLifecycleJdbc,
            SocialRemoteActionDispatcherJdbc remoteActionDispatcherJdbc,
            SocialRemoteActionLifecycleJdbc remoteActionLifecycleJdbc,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            SchedulerProperties properties,
            SchedulerMetrics metrics) {
        this.dispatcherJdbc = dispatcherJdbc;
        this.lifecycleJdbc = lifecycleJdbc;
        this.actionDispatcherJdbc = actionDispatcherJdbc;
        this.actionLifecycleJdbc = actionLifecycleJdbc;
        this.remoteActionDispatcherJdbc = remoteActionDispatcherJdbc;
        this.remoteActionLifecycleJdbc = remoteActionLifecycleJdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    public int dispatchDueBatch() {
        List<PublicationDispatchRow> claimed = transactionTemplate.execute(status ->
                dispatcherJdbc.claimDue(properties.batchSize(), properties.lockTtl(), properties.lookAhead()));
        if (claimed == null || claimed.isEmpty()) {
            metrics.recordDispatchIdle();
            return 0;
        }

        lastDispatchEpochMs.set(System.currentTimeMillis());
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

        List<UUID> failedConfirms = new ArrayList<>();
        long timeoutMs = properties.confirmTimeout().toMillis();
        for (int i = 0; i < correlations.size(); i++) {
            try {
                CorrelationData.Confirm confirm =
                        correlations.get(i).getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
                if (confirm == null || !confirm.isAck()) {
                    failedConfirms.add(claimed.get(i).id());
                }
            } catch (Exception ex) {
                log.warn(
                        "Publish confirm failed for publication {}: {}",
                        claimed.get(i).id(),
                        ex.toString());
                failedConfirms.add(claimed.get(i).id());
            }
        }
        if (!failedConfirms.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> lifecycleJdbc.revertQueuedToPending(failedConfirms));
            log.warn("Reverted {} publications to pending after missing broker confirms", failedConfirms.size());
            metrics.recordConfirmFailures(failedConfirms.size());
        }
        int ok = claimed.size() - failedConfirms.size();
        metrics.recordDispatched(ok);
        return ok;
    }

    public int dispatchDueActions() {
        List<ScheduledActionDispatchRow> claimed = transactionTemplate.execute(status ->
                actionDispatcherJdbc.claimDue(properties.batchSize(), properties.lockTtl(), properties.lookAhead()));
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
        long timeoutMs = properties.confirmTimeout().toMillis();
        for (int i = 0; i < correlations.size(); i++) {
            try {
                CorrelationData.Confirm confirm =
                        correlations.get(i).getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
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
        int ok = claimed.size() - failed.size();
        metrics.recordActionsDispatched(ok);
        return ok;
    }

    /** BE-33: claim due social_remote_actions and enqueue actions.moderation.{network}. */
    public int dispatchDueModerationActions() {
        List<SocialRemoteActionDispatchRow> claimed =
                transactionTemplate.execute(status -> remoteActionDispatcherJdbc.claimDue(
                        properties.batchSize(), properties.lockTtl(), properties.lookAhead()));
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
        long timeoutMs = properties.confirmTimeout().toMillis();
        for (int i = 0; i < correlations.size(); i++) {
            try {
                CorrelationData.Confirm confirm =
                        correlations.get(i).getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
                if (confirm == null || !confirm.isAck()) {
                    failed.add(claimed.get(i).id());
                }
            } catch (Exception ex) {
                log.warn(
                        "Moderation action confirm failed for {}: {}",
                        claimed.get(i).id(),
                        ex.toString());
                failed.add(claimed.get(i).id());
            }
        }
        if (!failed.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> remoteActionLifecycleJdbc.revertQueuedToPending(failed));
        }
        int ok = claimed.size() - failed.size();
        metrics.recordModerationActionsDispatched(ok);
        return ok;
    }

    public int reapStuck() {
        Integer updated = transactionTemplate.execute(status -> lifecycleJdbc.reaperStuck());
        int count = updated == null ? 0 : updated;
        Integer actions = transactionTemplate.execute(status -> actionLifecycleJdbc.reaperStuck());
        int actionCount = actions == null ? 0 : actions;
        Integer remote = transactionTemplate.execute(status -> remoteActionLifecycleJdbc.reaperStuck());
        int remoteCount = remote == null ? 0 : remote;
        if (count > 0 || actionCount > 0 || remoteCount > 0) {
            log.info(
                    "Reaper moved {} stuck publications, {} stuck actions, {} stuck remote actions to failed_retryable",
                    count,
                    actionCount,
                    remoteCount);
            metrics.recordReaper(count + actionCount + remoteCount);
        }
        return count + actionCount + remoteCount;
    }

    public long lastDispatchEpochMs() {
        return lastDispatchEpochMs.get();
    }
}
