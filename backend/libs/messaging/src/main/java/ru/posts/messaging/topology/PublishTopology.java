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

/** Declares publish / publish.dlx exchanges and per-network quorum queues (ADR-0005). */
@Configuration
public class PublishTopology {

    public static final String PUBLISH_EXCHANGE = "publish";
    public static final String PUBLISH_DLX = "publish.dlx";
    public static final int DELIVERY_LIMIT = 10;

    public static final List<String> NETWORKS = List.of("vk", "tg", "ok", "max", "rutube");

    public static String queueName(String network) {
        return "publish." + network;
    }

    public static String dlqName(String network) {
        return "publish." + network + ".dlq";
    }

    @Bean
    Declarables publishDeclarables() {
        DirectExchange publish = new DirectExchange(PUBLISH_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(PUBLISH_DLX, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(publish);
        declarables.add(dlx);

        for (String code : NETWORKS) {
            Queue queue = QueueBuilder.durable(queueName(code))
                    .withArgument("x-queue-type", "quorum")
                    .withArgument("x-dead-letter-exchange", PUBLISH_DLX)
                    .withArgument("x-dead-letter-routing-key", code)
                    .withArgument("x-delivery-limit", DELIVERY_LIMIT)
                    .build();
            Queue dlq = QueueBuilder.durable(dlqName(code))
                    .withArgument("x-queue-type", "quorum")
                    .build();
            Binding binding = BindingBuilder.bind(queue).to(publish).with(code);
            Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(code);
            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(binding);
            declarables.add(dlqBinding);
        }
        return new Declarables(declarables);
    }
}
