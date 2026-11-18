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
 * Declares {@code actions.moderation} / {@code actions.moderation.dlx} and per-network quorum queues
 * for BE-33 remote moderation.
 *
 * <p>Queues are declared for all {@link PublishTopology#NETWORKS} so every publisher-network pod
 * can bind its listener without missing-queue startup failures (ok/rutube queues stay idle).
 */
@Configuration
public class ModerationActionsTopology {

    public static final String MODERATION_ACTIONS_EXCHANGE = "actions.moderation";
    public static final String MODERATION_ACTIONS_DLX = "actions.moderation.dlx";
    public static final int DELIVERY_LIMIT = 10;

    /** Same network set as publish/actions so publisher pods always find a queue. */
    public static final List<String> NETWORKS = PublishTopology.NETWORKS;

    public static String queueName(String network) {
        return "actions.moderation." + network;
    }

    public static String dlqName(String network) {
        return "actions.moderation." + network + ".dlq";
    }

    @Bean
    Declarables moderationActionsDeclarables() {
        DirectExchange actions = new DirectExchange(MODERATION_ACTIONS_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(MODERATION_ACTIONS_DLX, true, false);
        List<Declarable> declarables = new ArrayList<>();
        declarables.add(actions);
        declarables.add(dlx);

        for (String code : NETWORKS) {
            Queue queue = QueueBuilder.durable(queueName(code))
                    .withArgument("x-queue-type", "quorum")
                    .withArgument("x-dead-letter-exchange", MODERATION_ACTIONS_DLX)
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
