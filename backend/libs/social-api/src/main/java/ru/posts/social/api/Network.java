package ru.posts.social.api;

/** Supported social networks (routing keys / publisher deployments). */
public enum Network {
    VK("vk"),
    TG("tg"),
    OK("ok"),
    MAX("max"),
    RUTUBE("rutube");

    private final String code;

    Network(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Network fromCode(String code) {
        for (Network network : values()) {
            if (network.code.equalsIgnoreCase(code)) {
                return network;
            }
        }
        throw new IllegalArgumentException("Unknown network: " + code);
    }
}
