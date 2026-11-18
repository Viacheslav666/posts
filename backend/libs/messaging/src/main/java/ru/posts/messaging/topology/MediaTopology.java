package ru.posts.messaging.topology;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares media / media.dlx exchanges and media.process quorum queue (ADR-0005). */
@Configuration
public class MediaTopology {

    public static final String MEDIA_EXCHANGE = "media";
    public static final String MEDIA_DLX = "media.dlx";
    public static final String ROUTING_KEY_PROCESS = "process";
    public static final String QUEUE_PROCESS = "media.process";
    public static final String QUEUE_PROCESS_DLQ = "media.process.dlq";
    public static final int DELIVERY_LIMIT = 10;

    @Bean
    Declarables mediaDeclarables() {
        DirectExchange media = new DirectExchange(MEDIA_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(MEDIA_DLX, true, false);

        Queue queue = QueueBuilder.durable(QUEUE_PROCESS)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-dead-letter-exchange", MEDIA_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_PROCESS)
                .withArgument("x-delivery-limit", DELIVERY_LIMIT)
                .build();
        Queue dlq = QueueBuilder.durable(QUEUE_PROCESS_DLQ)
                .withArgument("x-queue-type", "quorum")
                .build();
        Binding binding = BindingBuilder.bind(queue).to(media).with(ROUTING_KEY_PROCESS);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(ROUTING_KEY_PROCESS);

        return new Declarables(List.of(media, dlx, queue, dlq, binding, dlqBinding));
    }
}
