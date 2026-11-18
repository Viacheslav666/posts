package ru.posts.messaging.topology;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares ingest.grabber quorum queue + DLQ (BE-17 / ADR-0005). */
@Configuration
public class IngestTopology {

    public static final String INGEST_EXCHANGE = "ingest";
    public static final String INGEST_DLX = "ingest.dlx";
    public static final String GRABBER_ROUTING_KEY = "grabber";
    public static final String GRABBER_QUEUE = "ingest.grabber";
    public static final String GRABBER_DLQ = "ingest.grabber.dlq";
    public static final int DELIVERY_LIMIT = 10;

    @Bean
    Declarables ingestDeclarables() {
        DirectExchange ingest = new DirectExchange(INGEST_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(INGEST_DLX, true, false);
        Queue queue = QueueBuilder.durable(GRABBER_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-dead-letter-exchange", INGEST_DLX)
                .withArgument("x-dead-letter-routing-key", GRABBER_ROUTING_KEY)
                .withArgument("x-delivery-limit", DELIVERY_LIMIT)
                .build();
        Queue dlq = QueueBuilder.durable(GRABBER_DLQ)
                .withArgument("x-queue-type", "quorum")
                .build();
        Binding binding = BindingBuilder.bind(queue).to(ingest).with(GRABBER_ROUTING_KEY);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(GRABBER_ROUTING_KEY);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(ingest);
        declarables.add(dlx);
        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(binding);
        declarables.add(dlqBinding);
        return new Declarables(declarables);
    }
}
