package ru.posts.antispam.model;

import java.util.Locale;
import java.util.Optional;

/** Logical antispam section (content surface) within a channel. */
public enum AntispamSection {
    WALL_COMMENT,
    PHOTO_COMMENT,
    VIDEO_COMMENT,
    MARKET_COMMENT,
    BOARD_COMMENT,
    WALL_POST,
    SUGGESTED_POST,
    TG_DISCUSSION,
    TG_GROUP,
    MAX_COMMENT,
    MAX_CHAT;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AntispamSection> fromWire(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AntispamSection.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
