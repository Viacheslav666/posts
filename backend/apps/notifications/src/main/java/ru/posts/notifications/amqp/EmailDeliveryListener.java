package ru.posts.notifications.amqp;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.notifications.channel.ChannelDeliveryHandler;

@Component
public class EmailDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryListener.class);

    private final ChannelDeliveryHandler handler;

    public EmailDeliveryListener(ChannelDeliveryHandler handler) {
        this.handler = handler;
    }

    @RabbitListener(queues = NotifyTopology.EMAIL_QUEUE)
    public void onMessage(
            NotifyDeliveryCommand command, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        ackOrNack(command, channel, deliveryTag);
    }

    private void ackOrNack(NotifyDeliveryCommand command, Channel channel, long deliveryTag) throws IOException {
        try {
            boolean ack = handler.handle(command);
            if (ack) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("Unhandled email delivery error", ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
