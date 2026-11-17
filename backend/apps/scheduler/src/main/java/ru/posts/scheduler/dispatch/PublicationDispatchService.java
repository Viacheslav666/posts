package ru.posts.scheduler.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.messaging.publish.PublishCommand;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.persistence.jdbc.PublicationDispatchRow;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;
import ru.posts.scheduler.config.SchedulerProperties;

@Service
public class PublicationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PublicationDispatchService.class);

    private final PublicationDispatcherJdbc dispatcherJdbc;
    private final PublicationLifecycleJdbc lifecycleJdbc;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SchedulerProperties properties;

    public PublicationDispatchService(
            PublicationDispatcherJdbc dispatcherJdbc,
            PublicationLifecycleJdbc lifecycleJdbc,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            SchedulerProperties properties) {
        this.dispatcherJdbc = dispatcherJdbc;
        this.lifecycleJdbc = lifecycleJdbc;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public int dispatchDueBatch() {
        List<PublicationDispatchRow> claimed = transactionTemplate.execute(status ->
                dispatcherJdbc.claimDue(properties.batchSize(), properties.lockTtl(), properties.lookAhead()));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        // Publish the whole batch first, then wait for confirms (much faster than confirm-per-send).
        List<CorrelationData> correlations = new ArrayList<>(claimed.size());
        for (PublicationDispatchRow row : claimed) {
            CorrelationData correlation = new CorrelationData(row.id().toString());
            rabbitTemplate.convertAndSend(
                    PublishTopology.PUBLISH_EXCHANGE, row.network(), new PublishCommand(row.id(), null), correlation);
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
        }
        return claimed.size() - failedConfirms.size();
    }

    public int reapStuck() {
        Integer updated = transactionTemplate.execute(status -> lifecycleJdbc.reaperStuck());
        int count = updated == null ? 0 : updated;
        if (count > 0) {
            log.info("Reaper moved {} stuck publications to failed_retryable", count);
        }
        return count;
    }
}
