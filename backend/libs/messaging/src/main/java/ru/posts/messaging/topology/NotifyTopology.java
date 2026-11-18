package ru.posts.messaging.topology;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares notify.* quorum queues + DLQs (BE-11 / ADR-0005). */
@Configuration
public class NotifyTopology {

    public static final String NOTIFY_EXCHANGE = "notify";
    public static final String NOTIFY_DLX = "notify.dlx";

    public static final String EVENTS_QUEUE = "notify.events";
    public static final String EVENTS_DLQ = "notify.events.dlq";
    /** Domain events use routing key {@code event.<eventType>} so they do not collide with channel RKs. */
    public static final String EVENTS_RK_PREFIX = "event.";

    public static final String EMAIL_QUEUE = "notify.email";
    public static final String EMAIL_DLQ = "notify.email.dlq";
    public static final String EMAIL_RK = "email";

    public static final String TG_QUEUE = "notify.tg";
    public static final String TG_DLQ = "notify.tg.dlq";
    public static final String TG_RK = "tg";

    public static final String PUSH_QUEUE = "notify.push";
    public static final String PUSH_DLQ = "notify.push.dlq";
    public static final String PUSH_RK = "push";

    public static final String WS_QUEUE = "notify.ws";
    public static final String WS_DLQ = "notify.ws.dlq";
    public static final String WS_RK = "ws";

    /** Fan-out style topic for STOMP relay / API WS bridge. */
    public static final String WS_FANOUT_EXCHANGE = "notify.ws.fanout";

    public static final int DELIVERY_LIMIT = 10;

    public static String eventRoutingKey(String eventType) {
        return EVENTS_RK_PREFIX + (eventType == null ? "unknown" : eventType);
    }

    @Bean
    Declarables notifyDeclarables() {
        TopicExchange notify = new TopicExchange(NOTIFY_EXCHANGE, true, false);
        TopicExchange dlx = new TopicExchange(NOTIFY_DLX, true, false);
        TopicExchange wsFanout = new TopicExchange(WS_FANOUT_EXCHANGE, true, false);

        List<Declarable> declarables = new ArrayList<>();
        declarables.add(notify);
        declarables.add(dlx);
        declarables.add(wsFanout);

        // Domain events only (not email/tg/push/ws channel commands)
        addQueue(declarables, notify, dlx, EVENTS_QUEUE, EVENTS_DLQ, "event.#");
        addQueue(declarables, notify, dlx, EMAIL_QUEUE, EMAIL_DLQ, EMAIL_RK);
        addQueue(declarables, notify, dlx, TG_QUEUE, TG_DLQ, TG_RK);
        addQueue(declarables, notify, dlx, PUSH_QUEUE, PUSH_DLQ, PUSH_RK);
        addQueue(declarables, notify, dlx, WS_QUEUE, WS_DLQ, WS_RK);
        return new Declarables(declarables);
    }

    private static void addQueue(
            List<Declarable> declarables,
            TopicExchange notify,
            TopicExchange dlx,
            String queueName,
            String dlqName,
            String routingKey) {
        String dlqRoutingKey = routingKey.contains("#") || routingKey.contains("*") ? queueName : routingKey;
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-dead-letter-exchange", NOTIFY_DLX)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .withArgument("x-delivery-limit", DELIVERY_LIMIT)
                .build();
        Queue dlq = QueueBuilder.durable(dlqName)
                .withArgument("x-queue-type", "quorum")
                .build();
        Binding binding = BindingBuilder.bind(queue).to(notify).with(routingKey);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(dlqRoutingKey);
        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(binding);
        declarables.add(dlqBinding);
    }
}
