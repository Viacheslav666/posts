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
import ru.posts.messaging.publish.PublishCommand;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.persistence.jdbc.PublicationDispatchRow;
import ru.posts.persistence.jdbc.PublicationDispatcherJdbc;
import ru.posts.persistence.jdbc.PublicationLifecycleJdbc;

/** Test-only dispatcher mirroring posts-scheduler (avoids app→app dependency). */
@Component
public class TestDispatchHelper {

    private final PublicationDispatcherJdbc dispatcherJdbc;
    private final PublicationLifecycleJdbc lifecycleJdbc;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;

    public TestDispatchHelper(
            PublicationDispatcherJdbc dispatcherJdbc,
            PublicationLifecycleJdbc lifecycleJdbc,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate) {
        this.dispatcherJdbc = dispatcherJdbc;
        this.lifecycleJdbc = lifecycleJdbc;
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
            rabbitTemplate.convertAndSend(
                    PublishTopology.PUBLISH_EXCHANGE, row.network(), new PublishCommand(row.id(), null), correlation);
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

    public int reapStuck() {
        Integer updated = transactionTemplate.execute(status -> lifecycleJdbc.reaperStuck());
        return updated == null ? 0 : updated;
    }
}
