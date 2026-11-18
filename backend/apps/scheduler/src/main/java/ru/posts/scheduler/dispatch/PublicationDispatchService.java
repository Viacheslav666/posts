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
import ru.posts.messaging.publish.PublishCommand;
import ru.posts.messaging.topology.ActionsTopology;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.messaging.trace.AmqpTraceHeaders;
import ru.posts.persistence.jdbc.PublicationDispatchRow;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;
import ru.posts.persistence.jdbc.ScheduledActionDispatchRow;
import ru.posts.persistence.jdbc.ScheduledActionDispatcherJdbc;
import ru.posts.persistence.jdbc.ScheduledActionLifecycleJdbc;
import ru.posts.scheduler.config.SchedulerProperties;
import ru.posts.scheduler.metrics.SchedulerMetrics;

@Service
public class PublicationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PublicationDispatchService.class);

    private final PublicationDispatcherJdbc dispatcherJdbc;
    private final PublicationLifecycleJdbc lifecycleJdbc;
    private final ScheduledActionDispatcherJdbc actionDispatcherJdbc;
    private final ScheduledActionLifecycleJdbc actionLifecycleJdbc;
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
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            SchedulerProperties properties,
            SchedulerMetrics metrics) {
        this.dispatcherJdbc = dispatcherJdbc;
        this.lifecycleJdbc = lifecycleJdbc;
        this.actionDispatcherJdbc = actionDispatcherJdbc;
        this.actionLifecycleJdbc = actionLifecycleJdbc;
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

    public int reapStuck() {
        Integer updated = transactionTemplate.execute(status -> lifecycleJdbc.reaperStuck());
        int count = updated == null ? 0 : updated;
        Integer actions = transactionTemplate.execute(status -> actionLifecycleJdbc.reaperStuck());
        int actionCount = actions == null ? 0 : actions;
        if (count > 0 || actionCount > 0) {
            log.info("Reaper moved {} stuck publications and {} stuck actions to failed_retryable", count, actionCount);
            metrics.recordReaper(count + actionCount);
        }
        return count + actionCount;
    }

    public long lastDispatchEpochMs() {
        return lastDispatchEpochMs.get();
    }
}
