package ru.posts.notifications.pipeline;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.messaging.trace.AmqpTraceHeaders;

@Component
public class DeliveryCommandPublisher {

    private final RabbitTemplate rabbitTemplate;

    public DeliveryCommandPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(NotifyDeliveryCommand command) {
        String routingKey = routingKey(command.channel());
        if (routingKey == null) {
            return;
        }
        String trace = AmqpTraceHeaders.currentOrNew(command.traceId());
        rabbitTemplate.convertAndSend(
                NotifyTopology.NOTIFY_EXCHANGE, routingKey, command, AmqpTraceHeaders.withTraceparent(trace));
    }

    static String routingKey(String channel) {
        if (channel == null) {
            return null;
        }
        return switch (channel) {
            case NotificationChannels.EMAIL -> NotifyTopology.EMAIL_RK;
            case NotificationChannels.TELEGRAM -> NotifyTopology.TG_RK;
            case NotificationChannels.WEB_PUSH -> NotifyTopology.PUSH_RK;
            case NotificationChannels.WS -> NotifyTopology.WS_RK;
            default -> null;
        };
    }
}
