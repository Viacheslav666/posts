package ru.posts.ord.api;

/** Supported ORD providers. Ozon ({@code ozon_ord}) may be added later. */
public enum OrdProviderId {
    VK_ORD("vk_ord"),
    YANDEX_ORD("yandex_ord");

    private final String code;

    OrdProviderId(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static OrdProviderId fromCode(String code) {
        for (OrdProviderId id : values()) {
            if (id.code.equalsIgnoreCase(code)) {
                return id;
            }
        }
        throw new IllegalArgumentException("Unknown ORD provider: " + code);
    }
}
