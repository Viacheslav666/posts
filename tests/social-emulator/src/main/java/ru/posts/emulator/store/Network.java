package ru.posts.emulator.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Network {
    VK("vk"),
    TELEGRAM("telegram"),
    OK("ok"),
    MAX("max");

    private final String id;

    Network(String id) {
        this.id = id;
    }

    @JsonValue
    public String id() {
        return id;
    }

    @JsonCreator
    public static Network from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("network is required");
        }
        String normalized = value.trim().toLowerCase();
        for (Network network : values()) {
            if (network.id.equals(normalized) || network.name().equalsIgnoreCase(normalized)) {
                return network;
            }
        }
        throw new IllegalArgumentException("Unknown network: " + value);
    }
}
