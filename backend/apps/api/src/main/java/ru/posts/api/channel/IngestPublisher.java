package ru.posts.api.channel;

import com.fasterxml.jackson.databind.JsonNode;

/** Publishes validated social webhook payloads to the ingest pipeline. */
public interface IngestPublisher {
    void publish(String network, String resourceId, JsonNode body);
}
