package ru.posts.grabber.target;

import java.util.Locale;

/** Grabber targets are VK communities. Profiles and chats are rejected. */
public final class GrabberTargetGuard {

    private GrabberTargetGuard() {}

    public static void requireCommunity(String network, String type) {
        if (network == null || !"vk".equalsIgnoreCase(network.trim())) {
            throw new IllegalArgumentException("Целью граббера может быть только сообщество VK");
        }
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if ("profile".equals(normalized) || "chat".equals(normalized)) {
            throw new IllegalArgumentException("Целью граббера может быть только сообщество VK, не профиль и не чат");
        }
    }
}
