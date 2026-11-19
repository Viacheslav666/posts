package ru.posts.antispam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.posts.social.inbound.SocialInboundEvent;

/**
 * Detects comments under ad posts via {@code marked_as_ads} in raw payload / parent post markers.
 * ORD erid registry integration can be added later without changing the engine contract.
 */
public class AdsContextResolver {

    private final ObjectMapper objectMapper;

    public AdsContextResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isUnderAds(SocialInboundEvent event) {
        if (event.remoteIds() != null && "1".equals(event.remoteIds().get("marked_as_ads"))) {
            return true;
        }
        if (event.raw() == null || event.raw().isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(event.raw());
            JsonNode object = root.path("object");
            if (object.path("marked_as_ads").asInt(0) == 1) {
                return true;
            }
            if (object.path("post").path("marked_as_ads").asInt(0) == 1) {
                return true;
            }
            String text = event.text() == null ? "" : event.text().toLowerCase(java.util.Locale.ROOT);
            return text.contains("erid:");
        } catch (Exception ex) {
            return false;
        }
    }
}
