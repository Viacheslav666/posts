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

/**
 * Declares ingest exchange + grabber / social network quorum queues + DLQs (ADR-0005 / BE-17 / BE-32).
 */
@Configuration
public class IngestTopology {

    public static final String INGEST_EXCHANGE = "ingest";
    public static final String INGEST_DLX = "ingest.dlx";
    public static final String GRABBER_ROUTING_KEY = "grabber";
    public static final String GRABBER_QUEUE = "ingest.grabber";
    public static final String GRABBER_DLQ = "ingest.grabber.dlq";
    public static final int DELIVERY_LIMIT = 10;

    /** Social inbound routing keys / queues (BE-32). */
    public static final List<String> SOCIAL_NETWORKS = List.of("vk", "tg", "max");

    public static String socialQueue(String network) {
        return "ingest." + network;
    }

    public static String socialDlq(String network) {
        return "ingest." + network + ".dlq";
    }

    @Bean
    Declarables ingestDeclarables() {
        DirectExchange ingest = new DirectExchange(INGEST_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(INGEST_DLX, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(ingest);
        declarables.add(dlx);

        addQuorum(declarables, ingest, dlx, GRABBER_ROUTING_KEY, GRABBER_QUEUE, GRABBER_DLQ);
        for (String network : SOCIAL_NETWORKS) {
            addQuorum(declarables, ingest, dlx, network, socialQueue(network), socialDlq(network));
        }
        return new Declarables(declarables);
    }

    private static void addQuorum(
            List<Declarable> declarables,
            DirectExchange ingest,
            DirectExchange dlx,
            String routingKey,
            String queueName,
            String dlqName) {
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-dead-letter-exchange", INGEST_DLX)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .withArgument("x-delivery-limit", DELIVERY_LIMIT)
                .build();
        Queue dlq = QueueBuilder.durable(dlqName)
                .withArgument("x-queue-type", "quorum")
                .build();
        Binding binding = BindingBuilder.bind(queue).to(ingest).with(routingKey);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(routingKey);
        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(binding);
        declarables.add(dlqBinding);
    }
}
