package ru.posts.antispam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Optional;
import ru.posts.antispam.model.AntispamSection;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundEvent;

/** Maps inbound events to antispam sections. */
public class SectionResolver {

    private final ObjectMapper objectMapper;

    public SectionResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<AntispamSection> resolve(SocialInboundEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        String network = event.network() == null ? "" : event.network().toLowerCase(Locale.ROOT);
        return switch (network) {
            case "vk" -> resolveVk(event);
            case "tg", "telegram" -> resolveTg(event);
            case "max" -> resolveMax(event);
            default -> Optional.empty();
        };
    }

    private Optional<AntispamSection> resolveVk(SocialInboundEvent event) {
        if (event.kind() == SocialEventKind.POST_SUGGESTED) {
            return Optional.of(AntispamSection.SUGGESTED_POST);
        }
        if (event.kind() == SocialEventKind.POST_NEW) {
            return Optional.of(AntispamSection.WALL_POST);
        }
        String type = rawType(event);
        return switch (type) {
            case "photo_comment_new", "photo_comment_edit" -> Optional.of(AntispamSection.PHOTO_COMMENT);
            case "video_comment_new", "video_comment_edit" -> Optional.of(AntispamSection.VIDEO_COMMENT);
            case "market_comment_new", "market_comment_edit" -> Optional.of(AntispamSection.MARKET_COMMENT);
            case "board_post_new", "board_post_edit" -> Optional.of(AntispamSection.BOARD_COMMENT);
            case "wall_reply_new", "wall_reply_edit" -> Optional.of(AntispamSection.WALL_COMMENT);
            default -> event.kind() == SocialEventKind.COMMENT_NEW || event.kind() == SocialEventKind.COMMENT_EDITED
                    ? Optional.of(AntispamSection.WALL_COMMENT)
                    : Optional.empty();
        };
    }

    private Optional<AntispamSection> resolveTg(SocialInboundEvent event) {
        if (event.kind() != SocialEventKind.MESSAGE_NEW
                && event.kind() != SocialEventKind.COMMENT_NEW
                && event.kind() != SocialEventKind.COMMENT_EDITED) {
            return Optional.empty();
        }
        // Discussion groups linked to channels use MESSAGE_NEW; treat as TG_DISCUSSION by default.
        String chatType = event.remoteIds() == null ? "" : event.remoteIds().getOrDefault("chat_type", "");
        if ("supergroup".equals(chatType) || "group".equals(chatType)) {
            return Optional.of(AntispamSection.TG_DISCUSSION);
        }
        return Optional.of(AntispamSection.TG_DISCUSSION);
    }

    private Optional<AntispamSection> resolveMax(SocialInboundEvent event) {
        if (event.kind() == SocialEventKind.COMMENT_NEW || event.kind() == SocialEventKind.COMMENT_EDITED) {
            return Optional.of(AntispamSection.MAX_COMMENT);
        }
        if (event.kind() == SocialEventKind.MESSAGE_NEW) {
            return Optional.of(AntispamSection.MAX_CHAT);
        }
        return Optional.empty();
    }

    private String rawType(SocialInboundEvent event) {
        if (event.raw() == null || event.raw().isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(event.raw());
            return node.path("type").asText("");
        } catch (Exception ex) {
            return "";
        }
    }
}
