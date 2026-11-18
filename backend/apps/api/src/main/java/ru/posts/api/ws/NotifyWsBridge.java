package ru.posts.api.ws;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.posts.messaging.topology.NotifyTopology;

/**
 * Consumes notify.ws.fanout and pushes to STOMP {@code /user/queue/notifications}
 * and workspace publication topic (BE-11).
 */
@Configuration
@Profile("!openapi")
public class NotifyWsBridge {

    public static final String API_WS_QUEUE = "notify.ws.api";

    @Bean
    Queue notifyWsApiQueue() {
        return QueueBuilder.durable(API_WS_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .build();
    }

    @Bean
    Binding notifyWsApiBinding(@Qualifier("notifyWsApiQueue") Queue notifyWsApiQueue) {
        TopicExchange exchange = new TopicExchange(NotifyTopology.WS_FANOUT_EXCHANGE, true, false);
        return BindingBuilder.bind(notifyWsApiQueue).to(exchange).with("#");
    }

    @Component
    @Profile("!openapi")
    static class Listener {

        private static final Logger log = LoggerFactory.getLogger(Listener.class);

        private final SimpMessagingTemplate messagingTemplate;

        Listener(SimpMessagingTemplate messagingTemplate) {
            this.messagingTemplate = messagingTemplate;
        }

        @RabbitListener(queues = API_WS_QUEUE)
        public void onWsFanout(Map<String, Object> body) {
            if (body == null || body.isEmpty()) {
                return;
            }
            Object userIdRaw = body.get("userId");
            if (userIdRaw == null) {
                log.debug("WS fanout without userId — skip user queue");
            } else {
                String userId = userIdRaw.toString();
                messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", body);
            }
            Object workspaceIdRaw = body.get("workspaceId");
            Object eventType = body.get("eventType");
            if (workspaceIdRaw != null
                    && eventType != null
                    && eventType.toString().startsWith("publication.")) {
                try {
                    UUID workspaceId = UUID.fromString(workspaceIdRaw.toString());
                    messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId + "/publications", body);
                } catch (IllegalArgumentException ignored) {
                    // malformed workspace id
                }
            }
        }
    }
}
