package ru.posts.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.posts.grabber.GrabberRunProcessor;
import ru.posts.messaging.ingest.GrabberRunCommand;
import ru.posts.messaging.topology.IngestTopology;

@Component
public class GrabberRunListener {

    private static final Logger log = LoggerFactory.getLogger(GrabberRunListener.class);

    private final GrabberRunProcessor processor;

    public GrabberRunListener(GrabberRunProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = IngestTopology.GRABBER_QUEUE)
    public void onMessage(GrabberRunCommand command) {
        log.info(
                "Processing grabber run ruleId={} runId={} trigger={}",
                command.ruleId(),
                command.runId(),
                command.trigger());
        processor.process(command.ruleId(), command.runId(), command.trigger(), command.traceId());
    }
}
