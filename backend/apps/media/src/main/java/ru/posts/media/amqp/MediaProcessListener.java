package ru.posts.media.amqp;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import ru.posts.media.service.MediaProcessor;
import ru.posts.messaging.media.MediaProcessCommand;
import ru.posts.messaging.topology.MediaTopology;

@Component
public class MediaProcessListener {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessListener.class);

    private final MediaProcessor processor;

    public MediaProcessListener(MediaProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = MediaTopology.QUEUE_PROCESS)
    public void onMessage(
            MediaProcessCommand command, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        UUID mediaId = command == null ? null : command.mediaId();
        if (mediaId == null) {
            log.error("Dropping malformed media process command");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            boolean ack = processor.process(mediaId);
            if (ack) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("Unhandled error processing media {}", mediaId, ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
