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
 * Declares {@code actions} / {@code actions.dlx} exchanges and per-network quorum queues (ADR-0005
 * §Actions). Consumed by the same {@code publisher-<network>} as publish queues.
 */
@Configuration
public class ActionsTopology {

    public static final String ACTIONS_EXCHANGE = "actions";
    public static final String ACTIONS_DLX = "actions.dlx";
    public static final int DELIVERY_LIMIT = 10;

    public static String queueName(String network) {
        return "actions." + network;
    }

    public static String dlqName(String network) {
        return "actions." + network + ".dlq";
    }

    @Bean
    Declarables actionsDeclarables() {
        DirectExchange actions = new DirectExchange(ACTIONS_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(ACTIONS_DLX, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(actions);
        declarables.add(dlx);

        for (String code : PublishTopology.NETWORKS) {
            Queue queue = QueueBuilder.durable(queueName(code))
                    .withArgument("x-queue-type", "quorum")
                    .withArgument("x-dead-letter-exchange", ACTIONS_DLX)
                    .withArgument("x-dead-letter-routing-key", code)
                    .withArgument("x-delivery-limit", DELIVERY_LIMIT)
                    .build();
            Queue dlq = QueueBuilder.durable(dlqName(code))
                    .withArgument("x-queue-type", "quorum")
                    .build();
            Binding binding = BindingBuilder.bind(queue).to(actions).with(code);
            Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(code);
            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(binding);
            declarables.add(dlqBinding);
        }
        return new Declarables(declarables);
    }
}
