package ru.posts.notifications.amqp;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.posts.messaging.notify.NotifyDomainEvent;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.notifications.pipeline.DomainEventProcessor;

@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final DomainEventProcessor processor;

    public DomainEventListener(DomainEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = NotifyTopology.EVENTS_QUEUE)
    public void onMessage(NotifyDomainEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        try {
            boolean ack = processor.process(event);
            if (ack) {
                channel.basicAck(deliveryTag, false);
            } else {
                log.error("Poison notify domain event — nack without requeue");
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("Unhandled error on notify.events", ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
