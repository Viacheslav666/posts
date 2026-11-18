package ru.posts.scheduler.dispatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.ingest.GrabberRunCommand;
import ru.posts.messaging.topology.IngestTopology;
import ru.posts.persistence.entity.GrabberRunEntity;
import ru.posts.persistence.jdbc.AutomationRuleClaimJdbc;
import ru.posts.persistence.repository.GrabberRunRepository;
import ru.posts.scheduler.config.SchedulerProperties;

/** Claims due automation_rules (SKIP LOCKED) and enqueues ingest.grabber runs. */
@Service
public class GrabberDispatchService {

    private static final Logger log = LoggerFactory.getLogger(GrabberDispatchService.class);

    private final AutomationRuleClaimJdbc claimJdbc;
    private final GrabberRunRepository runRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final SchedulerProperties properties;

    public GrabberDispatchService(
            AutomationRuleClaimJdbc claimJdbc,
            GrabberRunRepository runRepository,
            RabbitTemplate rabbitTemplate,
            TransactionTemplate transactionTemplate,
            SchedulerProperties properties) {
        this.claimJdbc = claimJdbc;
        this.runRepository = runRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public int dispatchDueRules() {
        Instant now = Instant.now();
        // Lease next_run_at forward so competing schedulers don't double-claim; processor sets real next.
        Instant leaseUntil = now.plus(15, ChronoUnit.MINUTES);
        List<AutomationRuleClaimJdbc.DueAutomationRule> due = transactionTemplate.execute(
                status -> claimJdbc.claimDue(Math.min(100, properties.batchSize()), now, leaseUntil));
        if (due == null || due.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (AutomationRuleClaimJdbc.DueAutomationRule rule : due) {
            GrabberRunEntity run = new GrabberRunEntity();
            run.setId(UuidV7.generate());
            run.setRuleId(rule.ruleId());
            run.setWorkspaceId(rule.workspaceId());
            run.setTrigger("scheduled");
            run.setStatus("running");
            run.setStartedAt(now);
            String trace = UuidV7.generate().toString();
            run.setTraceId(trace);
            runRepository.save(run);
            rabbitTemplate.convertAndSend(
                    IngestTopology.INGEST_EXCHANGE,
                    IngestTopology.GRABBER_ROUTING_KEY,
                    new GrabberRunCommand(rule.ruleId(), run.getId(), "scheduled", trace));
            sent++;
        }
        log.debug("Enqueued {} grabber runs", sent);
        return sent;
    }
}
