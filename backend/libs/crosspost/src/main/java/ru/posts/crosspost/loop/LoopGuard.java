package ru.posts.crosspost.loop;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import ru.posts.content.hash.SimHasher;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.config.CrosspostProperties;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;

/**
 * Loop / duplicate protection layers L1–L4 and L7 helpers. L5 is {@link RuleGraphValidator}; L6 is
 * {@link CircuitBreaker}.
 */
public final class LoopGuard {

    public enum Layer {
        L1_OWN,
        L2_INTENT,
        L3_LINEAGE,
        L4_DEDUP,
        L7_BOT
    }

    public record Decision(boolean block, String status, String reason, Layer layer) {
        public static Decision allow() {
            return new Decision(false, null, null, null);
        }

        public static Decision block(String status, String reason, Layer layer) {
            return new Decision(true, status, reason, layer);
        }
    }

    private final RemotePostRegistryJdbc registry;
    private final CrosspostProperties properties;

    public LoopGuard(RemotePostRegistryJdbc registry, CrosspostProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /** L7: Max bot identity — skip when sender is our bot. */
    public Decision checkBotIdentity(String network, String senderUserId, String ourBotUserId) {
        if (!"max".equalsIgnoreCase(network)) {
            return Decision.allow();
        }
        if (senderUserId == null || ourBotUserId == null) {
            return Decision.allow();
        }
        if (senderUserId.equals(ourBotUserId)) {
            return Decision.block("skipped_own", "max_bot_identity", Layer.L7_BOT);
        }
        return Decision.allow();
    }

    /** L1 + optional suggest include. */
    public Decision checkOwnRegistry(UUID channelId, String remoteId, Map<String, Object> ruleConfig) {
        Optional<RemotePostRegistryJdbc.RegistryEntry> own = registry.findOwn(channelId, remoteId);
        if (own.isEmpty()) {
            return Decision.allow();
        }
        String origin = own.get().origin() == null ? "" : own.get().origin().toLowerCase(Locale.ROOT);
        if ("suggest".equals(origin)) {
            if (CrosspostConfigSupport.includeSuggestPublished(ruleConfig)) {
                return Decision.allow();
            }
            return Decision.block("skipped_own", "own_suggest_excluded", Layer.L1_OWN);
        }
        if ("publication".equals(origin) || "crosspost".equals(origin)) {
            if ("publication".equals(origin) && CrosspostConfigSupport.includePostsPublishedViaPosts(ruleConfig)) {
                return Decision.allow();
            }
            return Decision.block("skipped_own", "own_" + origin, Layer.L1_OWN);
        }
        return Decision.block("skipped_own", "own_" + origin, Layer.L1_OWN);
    }

    /** L2: fingerprint race against intents / recent registry. */
    public Decision checkIntentRace(UUID channelId, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return Decision.allow();
        }
        Instant since = Instant.now().minus(Duration.ofMinutes(10));
        if (registry.isOwnByFingerprint(channelId, fingerprint, since)) {
            return Decision.block("skipped_own", "own_fingerprint", Layer.L2_INTENT);
        }
        if (registry.isIntentOwn(channelId, fingerprint)) {
            return Decision.block("skipped_own", "own_intent", Layer.L2_INTENT);
        }
        return Decision.allow();
    }

    /**
     * L3: refuse publishing into visited channels or root origin channel; hop≥1 never becomes a
     * source (caller skips processing copies).
     */
    public Decision checkLineage(
            UUID targetChannelId,
            UUID sourceChannelId,
            Map<String, Object> sourceRef,
            short hop) {
        if (hop >= 1) {
            return Decision.block("skipped_loop", "hop_copy_not_source", Layer.L3_LINEAGE);
        }
        Set<UUID> visited = new HashSet<>();
        visited.add(sourceChannelId);
        Object visitedRaw = sourceRef == null ? null : sourceRef.get("visited_channel_ids");
        if (visitedRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    try {
                        visited.add(UUID.fromString(o.toString()));
                    } catch (IllegalArgumentException ignored) {
                        // ignore malformed
                    }
                }
            }
        }
        if (sourceRef != null) {
            Object root = sourceRef.get("root_origin");
            if (root instanceof Map<?, ?> m) {
                Object rootChannel = m.get("channel_id");
                if (rootChannel != null) {
                    try {
                        visited.add(UUID.fromString(rootChannel.toString()));
                    } catch (IllegalArgumentException ignored) {
                        // ignore
                    }
                }
            }
        }
        if (visited.contains(targetChannelId)) {
            return Decision.block("skipped_loop", "visited_or_root", Layer.L3_LINEAGE);
        }
        return Decision.allow();
    }

    /** L4: SimHash distance ≤ N or exact fingerprint match on target channel (24h). */
    public Decision checkTargetDedup(UUID targetChannelId, String fingerprint, long simhash) {
        Instant since = Instant.now().minus(properties.getDedupWindow());
        List<String> recent = registry.listRecentFingerprints(targetChannelId, since, 200);
        if (fingerprint != null && !fingerprint.isBlank()) {
            for (String fp : recent) {
                if (fingerprint.equals(fp)) {
                    return Decision.block("skipped_duplicate", "exact_fingerprint", Layer.L4_DEDUP);
                }
                if (nearDuplicate(fingerprint, fp, simhash)) {
                    return Decision.block("skipped_duplicate", "simhash_near", Layer.L4_DEDUP);
                }
            }
        }
        return Decision.allow();
    }

    private boolean nearDuplicate(String fingerprint, String other, long simhash) {
        Long otherSim = parseSim(other);
        if (otherSim == null) {
            return false;
        }
        Long left = simhash != 0L ? Long.valueOf(simhash) : parseSim(fingerprint);
        if (left == null) {
            return false;
        }
        return SimHasher.hammingDistance(left, otherSim) <= properties.getDedupHammingDistance();
    }

    private static Long parseSim(String fingerprint) {
        if (fingerprint == null) {
            return null;
        }
        int idx = fingerprint.indexOf("sim:");
        if (idx < 0) {
            return null;
        }
        int end = fingerprint.indexOf('|', idx);
        String hex = end < 0 ? fingerprint.substring(idx + 4) : fingerprint.substring(idx + 4, end);
        try {
            return Long.parseUnsignedLong(hex.trim(), 16);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Map<String, Object> buildSourceRef(
            UUID ruleId,
            UUID itemId,
            String network,
            UUID sourceChannelId,
            String remoteId,
            Map<String, Object> prior) {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        if (prior != null && prior.get("root_origin") instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> root.put(String.valueOf(k), v));
        } else {
            root.put("network", network);
            root.put("channel_id", sourceChannelId.toString());
            root.put("remote_id", remoteId);
        }
        List<String> visited = new ArrayList<>();
        if (prior != null && prior.get("visited_channel_ids") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    visited.add(o.toString());
                }
            }
        }
        if (!visited.contains(sourceChannelId.toString())) {
            visited.add(sourceChannelId.toString());
        }
        int hop = 0;
        if (prior != null && prior.get("hop") instanceof Number n) {
            hop = n.intValue() + 1;
        } else {
            hop = 1;
        }
        Map<String, Object> ref = new java.util.LinkedHashMap<>();
        ref.put("ruleId", ruleId.toString());
        ref.put("itemId", itemId.toString());
        ref.put("root_origin", root);
        ref.put("hop", hop);
        ref.put("visited_channel_ids", visited);
        return ref;
    }
}
