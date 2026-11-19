package ru.posts.crosspost.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;

/** Resolves hop / root_origin / visited for L3 from registry and item payload (BE-36). */
public final class CrosspostLineageResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CrosspostLineageResolver() {}

    public record Resolved(short hop, Map<String, Object> sourceRef) {}

    public static Resolved resolve(CrosspostItemEntity item, RemotePostRegistryJdbc registry) {
        Map<String, Object> sourceRef = lineageFromPayload(item.getPayload());
        short hop = hopFromPayload(item.getPayload());

        Optional<RemotePostRegistryJdbc.RegistryEntry> own =
                registry.findOwn(item.getSourceChannelId(), item.getSourceRemoteId());
        if (own.isPresent()) {
            Resolved fromRegistry = fromRegistry(own.get(), item.getSourceChannelId());
            sourceRef = mergeSourceRef(sourceRef, fromRegistry.sourceRef());
            hop = (short) Math.max(hop, fromRegistry.hop());
        }

        if (sourceRef.isEmpty()) {
            sourceRef = defaultSourceRef(item);
        }
        return new Resolved(hop, sourceRef);
    }

    /** Merge outbound lineage into a newly created inbound item payload after a successful publish. */
    public static Map<String, Object> outboundLineage(
            CrosspostItemEntity item, UUID ruleId, Map<String, Object> existingPayload) {
        Map<String, Object> prior = existingPayload == null ? Map.of() : existingPayload;
        return LoopGuard.buildSourceRef(
                ruleId,
                item.getId(),
                item.getSourceNetwork(),
                item.getSourceChannelId(),
                item.getSourceRemoteId(),
                prior);
    }

    public static Map<String, Object> mergePayload(Map<String, Object> base, Map<String, Object> lineage) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (base != null) {
            out.putAll(base);
        }
        if (lineage != null) {
            out.putAll(lineage);
        }
        return out;
    }

    /** Copy resolved hop / root_origin / visited onto an inbound item payload. */
    public static void applyResolved(Map<String, Object> payload, Resolved resolved) {
        if (payload == null || resolved == null) {
            return;
        }
        payload.put("hop", resolved.hop());
        if (resolved.sourceRef().containsKey("root_origin")) {
            payload.put("root_origin", resolved.sourceRef().get("root_origin"));
        }
        if (resolved.sourceRef().containsKey("visited_channel_ids")) {
            payload.put("visited_channel_ids", resolved.sourceRef().get("visited_channel_ids"));
        }
    }

    private static Resolved fromRegistry(RemotePostRegistryJdbc.RegistryEntry entry, UUID sourceChannelId) {
        Map<String, Object> sourceRef = new LinkedHashMap<>();
        Map<String, Object> root = parseRootOrigin(entry.rootOriginJson());
        if (!root.isEmpty()) {
            sourceRef.put("root_origin", root);
        }
        List<String> visited = new ArrayList<>();
        visited.add(sourceChannelId.toString());
        Object rootChannel = root.get("channel_id");
        if (rootChannel != null) {
            visited.add(rootChannel.toString());
        }
        sourceRef.put("visited_channel_ids", visited);
        return new Resolved(entry.hop(), sourceRef);
    }

    private static Map<String, Object> lineageFromPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> ref = new LinkedHashMap<>();
        Object root = payload.get("root_origin");
        if (root instanceof Map<?, ?> m) {
            Map<String, Object> rootMap = new LinkedHashMap<>();
            m.forEach((k, v) -> rootMap.put(String.valueOf(k), v));
            ref.put("root_origin", rootMap);
        }
        Object visited = payload.get("visited_channel_ids");
        if (visited instanceof List<?> list) {
            ref.put("visited_channel_ids", new ArrayList<>(list));
        }
        return ref;
    }

    private static short hopFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return 0;
        }
        Object hop = payload.get("hop");
        if (hop instanceof Number n) {
            return n.shortValue();
        }
        return 0;
    }

    private static Map<String, Object> defaultSourceRef(CrosspostItemEntity item) {
        return Map.of(
                "root_origin",
                Map.of(
                        "network",
                        item.getSourceNetwork() == null ? "" : item.getSourceNetwork(),
                        "channel_id",
                        item.getSourceChannelId().toString(),
                        "remote_id",
                        item.getSourceRemoteId() == null ? "" : item.getSourceRemoteId()),
                "hop",
                0,
                "visited_channel_ids",
                List.of(item.getSourceChannelId().toString()));
    }

    private static Map<String, Object> mergeSourceRef(Map<String, Object> left, Map<String, Object> right) {
        if (left == null || left.isEmpty()) {
            return right == null ? Map.of() : new LinkedHashMap<>(right);
        }
        if (right == null || right.isEmpty()) {
            return new LinkedHashMap<>(left);
        }
        Map<String, Object> merged = new LinkedHashMap<>(left);
        for (Map.Entry<String, Object> e : right.entrySet()) {
            if ("visited_channel_ids".equals(e.getKey())
                    && e.getValue() instanceof List<?> rightList
                    && merged.get("visited_channel_ids") instanceof List<?> leftList) {
                List<String> union = new ArrayList<>();
                for (Object o : leftList) {
                    if (o != null) {
                        union.add(o.toString());
                    }
                }
                for (Object o : rightList) {
                    String s = o == null ? "" : o.toString();
                    if (!union.contains(s)) {
                        union.add(s);
                    }
                }
                merged.put("visited_channel_ids", union);
            } else {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return merged;
    }

    private static Map<String, Object> parseRootOrigin(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Map.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            Map<String, Object> out = new LinkedHashMap<>();
            if (node.has("network")) {
                out.put("network", node.get("network").asText());
            }
            if (node.has("channel_id")) {
                out.put("channel_id", node.get("channel_id").asText());
            }
            if (node.has("remote_post_id")) {
                out.put("remote_post_id", node.get("remote_post_id").asText());
            } else if (node.has("remote_id")) {
                out.put("remote_id", node.get("remote_id").asText());
            }
            return out;
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
