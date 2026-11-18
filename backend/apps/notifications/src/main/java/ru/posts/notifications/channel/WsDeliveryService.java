package ru.posts.notifications.channel;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import ru.posts.messaging.notify.NotifyDeliveryCommand;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.messaging.trace.AmqpTraceHeaders;

@Service
public class WsDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WsDeliveryService.class);

    private final RabbitTemplate rabbitTemplate;

    public WsDeliveryService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public DeliveryOutcome deliver(NotifyDeliveryCommand command) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("notificationId", command.notificationId());
            body.put("userId", command.userId());
            body.put("workspaceId", command.workspaceId());
            body.put("eventType", command.eventType());
            body.put("dedupeKey", command.dedupeKey());
            body.put("payload", command.payload());
            String trace = AmqpTraceHeaders.currentOrNew(command.traceId());
            rabbitTemplate.convertAndSend(
                    NotifyTopology.WS_FANOUT_EXCHANGE, "notification", body, AmqpTraceHeaders.withTraceparent(trace));
            return DeliveryOutcome.sent("ws:fanout");
        } catch (Exception ex) {
            log.warn("WS fanout failed for {}: {}", command.deliveryId(), ex.toString());
            return DeliveryOutcome.failed(ex.getMessage());
        }
    }
}
