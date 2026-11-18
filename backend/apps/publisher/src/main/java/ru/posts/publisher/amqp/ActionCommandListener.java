package ru.posts.publisher.amqp;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.posts.messaging.publish.ActionCommand;
import ru.posts.publisher.metrics.PublicationMetrics;
import ru.posts.publisher.service.ActionProcessor;

@Component
public class ActionCommandListener {

    private static final Logger log = LoggerFactory.getLogger(ActionCommandListener.class);

    private final ActionProcessor processor;
    private final PublicationMetrics metrics;
    private final String network;

    public ActionCommandListener(
            ActionProcessor processor,
            PublicationMetrics metrics,
            ru.posts.publisher.config.PublisherProperties properties) {
        this.processor = processor;
        this.metrics = metrics;
        this.network = properties.network();
    }

    @RabbitListener(queues = "actions.${posts.publisher.network}")
    public void onMessage(ActionCommand command, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        UUID actionId = command == null ? null : command.actionId();
        if (actionId == null) {
            log.error("Dropping malformed action command");
            metrics.recordDlq(network, "actions");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            boolean ack = processor.process(actionId);
            if (ack) {
                channel.basicAck(deliveryTag, false);
            } else {
                metrics.recordDlq(network, "actions");
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("Unhandled error processing action {}", actionId, ex);
            metrics.recordDlq(network, "actions");
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
