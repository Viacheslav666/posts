package ru.posts.emulator.vk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * In-memory VK wall source fixtures for grabber ingest (QA-12 / TC-GRAB).
 * Seed via {@code PUT /_control/vk/wall/{ownerId}}.
 */
@Component
public class WallSourceStore {

    private final ConcurrentMap<String, List<Map<String, Object>>> walls = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1_000);

    public void reset() {
        walls.clear();
        idSeq.set(1_000);
    }

    public void replace(String ownerId, List<Map<String, Object>> items) {
        String key = normalizeOwner(ownerId);
        List<Map<String, Object>> copy = new ArrayList<>();
        if (items != null) {
            for (Map<String, Object> item : items) {
                copy.add(normalizeItem(key, item));
            }
        }
        walls.put(key, copy);
    }

    public Map<String, Object> add(String ownerId, Map<String, Object> item) {
        String key = normalizeOwner(ownerId);
        Map<String, Object> normalized = normalizeItem(key, item);
        walls.compute(key, (k, existing) -> {
            List<Map<String, Object>> list = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            list.add(0, normalized);
            return list;
        });
        return normalized;
    }

    public Map<String, Object> wallGet(String ownerId, int count) {
        String key = normalizeOwner(ownerId);
        List<Map<String, Object>> items = walls.getOrDefault(key, List.of());
        int limit = count <= 0 ? items.size() : Math.min(count, items.size());
        List<Map<String, Object>> slice = items.subList(0, limit);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", items.size());
        response.put("items", slice);
        return Map.of("response", response);
    }

    /** Seeded attachment payload (e.g. a video for {@code video.get}) by type / owner / id. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findAttachment(String type, String ownerId, String itemId) {
        for (List<Map<String, Object>> items : walls.values()) {
            for (Map<String, Object> item : items) {
                if (!(item.get("attachments") instanceof List<?> atts)) {
                    continue;
                }
                for (Object raw : atts) {
                    if (!(raw instanceof Map<?, ?> att) || !type.equals(String.valueOf(att.get("type")))) {
                        continue;
                    }
                    if (att.get(type) instanceof Map<?, ?> payload
                            && ownerId.equals(String.valueOf(payload.get("owner_id")))
                            && itemId.equals(String.valueOf(payload.get("id")))) {
                        return Optional.of(new LinkedHashMap<>((Map<String, Object>) payload));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        walls.forEach((owner, items) -> out.put(owner, List.copyOf(items)));
        return out;
    }

    private Map<String, Object> normalizeItem(String ownerId, Map<String, Object> raw) {
        Map<String, Object> item = new LinkedHashMap<>();
        long id = raw != null && raw.get("id") instanceof Number n ? n.longValue() : idSeq.incrementAndGet();
        item.put("id", id);
        item.put("owner_id", Long.parseLong(ownerId));
        item.put("from_id", Long.parseLong(ownerId));
        item.put("text", raw == null ? "" : String.valueOf(raw.getOrDefault("text", "")));
        item.put("marked_as_ads", boolOrInt(raw == null ? null : raw.get("marked_as_ads"), 0));
        item.put("attachments", raw != null && raw.get("attachments") instanceof List<?> list ? list : List.of());
        if (raw != null && raw.get("copy_history") instanceof List<?> copy) {
            item.put("copy_history", copy);
        }
        if (raw != null && raw.containsKey("date")) {
            item.put("date", raw.get("date"));
        } else {
            item.put("date", System.currentTimeMillis() / 1000L);
        }
        return item;
    }

    private static Object boolOrInt(Object value, int defaultValue) {
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private static String normalizeOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return "0";
        }
        String trimmed = ownerId.trim();
        if (trimmed.startsWith("club")) {
            return "-" + trimmed.substring(4);
        }
        return trimmed;
    }
}
