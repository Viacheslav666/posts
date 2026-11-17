package ru.posts.api.channel;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default ingest publisher for MVP: validates hooks without requiring RabbitMQ on posts-api.
 * A Rabbit-backed bean can replace this when messaging is wired in the deployment.
 */
public class LoggingIngestPublisher implements IngestPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingIngestPublisher.class);

    @Override
    public void publish(String network, String resourceId, JsonNode body) {
        log.info(
                "ingest_enqueued network={} resourceId={} bytes={}",
                network,
                resourceId,
                body == null ? 0 : body.toString().length());
    }
}
