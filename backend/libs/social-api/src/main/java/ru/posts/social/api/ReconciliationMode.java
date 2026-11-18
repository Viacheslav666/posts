package ru.posts.social.api;

import java.util.Locale;

/**
 * How {@link SocialAdapter#findExisting} resolves unknown outcomes (ADR-0012 / BE-23).
 *
 * <ul>
 *   <li>{@link #EMULATOR} — control API {@code /_control/posts} (tests / local emulator only)
 *   <li>{@link #API} — real network API (VK {@code wall.get}, OK feed / getByIds)
 * </ul>
 */
public enum ReconciliationMode {
    EMULATOR,
    API;

    public static ReconciliationMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return API;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "emulator", "control", "test" -> EMULATOR;
            default -> API;
        };
    }
}
