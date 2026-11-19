package ru.posts.social.inbound;

import java.util.Map;

/** Mutable per-dispatch context for handlers. */
public final class HandlerContext {

    private final Map<String, Object> attributes;

    public HandlerContext(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public Object get(String key) {
        return attributes.get(key);
    }

    public void put(String key, Object value) {
        attributes.put(key, value);
    }
}
