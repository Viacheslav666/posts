package ru.posts.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.posts.messaging.ingest.SocialInboundCommand;
import ru.posts.social.inbound.SocialInboundProcessor;

@Component
public class SocialInboundListener {

    private static final Logger log = LoggerFactory.getLogger(SocialInboundListener.class);

    private final SocialInboundProcessor processor;

    public SocialInboundListener(SocialInboundProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "ingest.vk")
    public void onVk(SocialInboundCommand command) {
        handle(command);
    }

    @RabbitListener(queues = "ingest.tg")
    public void onTg(SocialInboundCommand command) {
        handle(command);
    }

    @RabbitListener(queues = "ingest.max")
    public void onMax(SocialInboundCommand command) {
        handle(command);
    }

    private void handle(SocialInboundCommand command) {
        log.info(
                "social_inbound_received network={} resourceId={} retryCounter={}",
                command.network(),
                command.resourceId(),
                command.retryCounter());
        processor.process(command);
    }
}
