package ru.posts.api.channel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.posts.messaging.ingest.SocialInboundCommand;

class RabbitIngestPublisherTest {

    @Test
    void brokerDownThrowsUnavailable() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new java.net.ConnectException("down")))
                .when(template)
                .convertAndSend(eq("ingest"), eq("vk"), any(SocialInboundCommand.class), any(CorrelationData.class));
        RabbitIngestPublisher publisher = new RabbitIngestPublisher(template);
        assertThatThrownBy(() -> publisher.publish("vk", "ch-1", JsonNodeFactory.instance.objectNode()))
                .isInstanceOf(IngestBrokerUnavailableException.class);
    }
}
