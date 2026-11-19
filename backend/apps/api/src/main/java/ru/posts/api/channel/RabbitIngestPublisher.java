package ru.posts.api.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.posts.messaging.ingest.SocialInboundCommand;
import ru.posts.messaging.topology.IngestTopology;

/**
 * Publishes validated webhook payloads to ingest.{vk|tg|max} with publisher confirms (BE-32).
 * Throws {@link IngestBrokerUnavailableException} when the broker is down so hooks can return 503.
 */
public class RabbitIngestPublisher implements IngestPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitIngestPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitIngestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String network, String resourceId, JsonNode body) {
        publish(network, resourceId, body, null);
    }

    public void publish(String network, String resourceId, JsonNode body, Integer retryCounter) {
        JsonNode sanitized = stripSecrets(body);
        SocialInboundCommand command = new SocialInboundCommand(
                network,
                resourceId,
                sanitized.toString(),
                Instant.now(),
                UUID.randomUUID().toString(),
                retryCounter);
        try {
            CorrelationData correlation = new CorrelationData(command.traceId());
            rabbitTemplate.convertAndSend(IngestTopology.INGEST_EXCHANGE, network, command, correlation);
            log.info(
                    "ingest_enqueued network={} resourceId={} bytes={} retryCounter={}",
                    network,
                    resourceId,
                    command.payloadJson().length(),
                    retryCounter);
        } catch (AmqpException ex) {
            log.warn("ingest_broker_unavailable network={} resourceId={} err={}", network, resourceId, ex.toString());
            throw new IngestBrokerUnavailableException("RabbitMQ unavailable", ex);
        }
    }

    private static JsonNode stripSecrets(JsonNode body) {
        if (body instanceof ObjectNode obj) {
            ObjectNode copy = obj.deepCopy();
            copy.remove("secret");
            return copy;
        }
        return body;
    }
}
