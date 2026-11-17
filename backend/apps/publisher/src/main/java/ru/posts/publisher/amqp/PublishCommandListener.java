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
import ru.posts.messaging.publish.PublishCommand;
import ru.posts.publisher.service.PublicationProcessor;

@Component
public class PublishCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PublishCommandListener.class);

    private final PublicationProcessor processor;

    public PublishCommandListener(PublicationProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "publish.${posts.publisher.network}")
    public void onMessage(PublishCommand command, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        UUID publicationId = command == null ? null : command.publicationId();
        if (publicationId == null) {
            log.error("Dropping malformed publish command");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            boolean ack = processor.process(publicationId);
            if (ack) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("Unhandled error processing publication {}", publicationId, ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
