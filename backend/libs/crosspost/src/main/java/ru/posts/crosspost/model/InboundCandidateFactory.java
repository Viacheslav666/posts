package ru.posts.crosspost.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.posts.crosspost.text.CrosspostTextSignals;
import ru.posts.social.inbound.SocialInboundAttachment;
import ru.posts.social.inbound.SocialInboundEvent;

/** Builds {@link CrosspostCandidate} from inbound SPI events + raw payload hints. */
public final class InboundCandidateFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InboundCandidateFactory() {}

    public static CrosspostCandidate fromEvent(SocialInboundEvent event) {
        List<Map<String, Object>> attachments = new ArrayList<>();
        boolean hasImage = false;
        boolean hasVideo = false;
        boolean hasClip = false;
        boolean hasAudio = false;
        boolean hasPoll = false;
        boolean hasGeo = false;
        boolean hasLink = false;
        boolean hasSnippet = false;
        boolean hasArticle = false;
        if (event.attachments() != null) {
            for (SocialInboundAttachment a : event.attachments()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String type = a.type() == null ? "file" : a.type().toLowerCase(Locale.ROOT);
                row.put("type", type);
                row.put("remoteRef", a.remoteRef());
                row.put("url", a.url());
                row.put("sizeBytes", a.sizeBytes());
                row.put("mimeType", a.mimeType());
                attachments.add(row);
                switch (type) {
                    case "photo", "image" -> hasImage = true;
                    case "video" -> hasVideo = true;
                    case "clip", "short_video" -> hasClip = true;
                    case "audio", "voice" -> hasAudio = true;
                    case "poll" -> hasPoll = true;
                    case "geo", "location" -> hasGeo = true;
                    case "link" -> hasLink = true;
                    case "snippet", "page" -> hasSnippet = true;
                    case "article", "doc_article" -> hasArticle = true;
                    default -> {
                        // keep as generic attachment
                    }
                }
            }
        }

        JsonNode raw = parseRaw(event.raw());
        boolean markedAsAds = raw.path("object").path("marked_as_ads").asInt(0) == 1
                || raw.path("marked_as_ads").asInt(0) == 1
                || raw.path("object").path("marked_as_ads").asBoolean(false);
        boolean isRepost = raw.path("object").has("copy_history")
                || "wall_repost".equals(raw.path("type").asText(""))
                || raw.path("forward_origin").isObject()
                || raw.path("message").path("forward_origin").isObject();
        boolean hasErid = CrosspostTextSignals.containsErid(event.text())
                || raw.toString().toLowerCase(Locale.ROOT).contains("erid");
        if (raw.path("object").has("geo") || raw.path("geo").isObject()) {
            hasGeo = true;
        }
        if (raw.path("object").has("attachments")) {
            for (JsonNode att : raw.path("object").path("attachments")) {
                String t = att.path("type").asText("");
                if ("poll".equals(t)) {
                    hasPoll = true;
                }
                if ("link".equals(t)) {
                    hasLink = true;
                    if (att.path("link").path("button").isObject()
                            || "article".equals(att.path("link").path("button").path("action").path("type").asText(""))) {
                        hasArticle = true;
                    }
                    if (att.path("link").has("photo") || att.path("link").has("product")) {
                        hasSnippet = true;
                    }
                }
                if ("video".equals(t) && "clip".equals(att.path("video").path("type").asText(""))) {
                    hasClip = true;
                }
            }
        }

        boolean fromGroup = event.author() != null && event.author().community();
        String remoteId = remoteId(event);
        return CrosspostCandidate.builder()
                .network(event.network())
                .remoteId(remoteId)
                .text(event.text())
                .attachments(attachments)
                .markedAsAds(markedAsAds)
                .hasErid(hasErid)
                .isRepost(isRepost)
                .fromGroup(fromGroup)
                .hasPoll(hasPoll)
                .hasGeo(hasGeo)
                .hasVideo(hasVideo)
                .hasClip(hasClip)
                .hasAudio(hasAudio)
                .hasImage(hasImage)
                .hasLinkAttachment(hasLink)
                .hasSnippet(hasSnippet)
                .hasArticle(hasArticle)
                .publishedAt(event.occurredAt())
                .rawMeta(Map.of(
                        "dedupKey", event.dedupKey() == null ? "" : event.dedupKey(),
                        "authorId", event.author() == null ? "" : String.valueOf(event.author().id()),
                        "authorBot", event.author() != null && event.author().bot()))
                .build();
    }

    public static String remoteId(SocialInboundEvent event) {
        if (event.remoteIds() != null) {
            if (event.remoteIds().get("post_id") != null) {
                return event.remoteIds().get("post_id");
            }
            if (event.remoteIds().get("message_id") != null) {
                return event.remoteIds().get("message_id");
            }
            if (event.remoteIds().get("mid") != null) {
                return event.remoteIds().get("mid");
            }
        }
        return event.dedupKey() == null ? "unknown" : event.dedupKey();
    }

    private static JsonNode parseRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return MAPPER.missingNode();
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception ex) {
            return MAPPER.missingNode();
        }
    }
}
