package ru.posts.emulator.store;

import java.time.Instant;
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
 * In-memory stores for BE-33 moderation: VK suggests, comments (deleted/restored), bans.
 */
@Component
public class ModerationStore {

    private final ConcurrentMap<String, SuggestedPost> suggests = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, EmulatedComment> comments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Object>> commentMeta = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BannedUser> bans = new ConcurrentHashMap<>();
    private final AtomicLong commentSeq = new AtomicLong();
    private final AtomicLong suggestSeq = new AtomicLong(10_000);

    public void reset() {
        suggests.clear();
        comments.clear();
        commentMeta.clear();
        bans.clear();
        commentSeq.set(0);
        suggestSeq.set(10_000);
    }

    public void putCommentMeta(String commentId, Map<String, Object> meta) {
        if (commentId == null) {
            return;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        if (meta != null) {
            meta.forEach((key, value) -> {
                if (key != null && value != null) {
                    copy.put(key, value);
                }
            });
        }
        commentMeta.put(commentId, Map.copyOf(copy));
    }

    public Map<String, Object> commentMeta(String commentId) {
        if (commentId == null) {
            return Map.of();
        }
        return commentMeta.getOrDefault(commentId, Map.of());
    }

    public void clearSuggests() {
        suggests.clear();
        suggestSeq.set(10_000);
    }

    // --- Suggests (VK) ---

    public SuggestedPost putSuggest(String ownerId, Long postId, Map<String, Object> fields) {
        String owner = normalizeOwner(ownerId);
        long id = postId != null ? postId : suggestSeq.incrementAndGet();
        Map<String, Object> data = new LinkedHashMap<>();
        if (fields != null) {
            data.putAll(fields);
        }
        data.putIfAbsent("id", id);
        data.putIfAbsent("owner_id", Long.parseLong(owner));
        data.putIfAbsent("from_id", data.getOrDefault("from_id", 1L));
        data.putIfAbsent("text", data.getOrDefault("text", ""));
        data.putIfAbsent("post_type", "suggest");
        data.putIfAbsent("date", Instant.now().getEpochSecond());
        SuggestedPost suggest = new SuggestedPost(owner, id, Map.copyOf(data), Instant.now());
        suggests.put(suggestKey(owner, id), suggest);
        return suggest;
    }

    public void replaceSuggests(String ownerId, List<Map<String, Object>> items) {
        String owner = normalizeOwner(ownerId);
        suggests.keySet().removeIf(key -> key.startsWith(owner + "|"));
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            Long id = null;
            if (item.get("id") instanceof Number n) {
                id = n.longValue();
            } else if (item.get("post_id") instanceof Number n) {
                id = n.longValue();
            }
            putSuggest(owner, id, item);
        }
    }

    public Optional<SuggestedPost> findSuggest(String ownerId, long postId) {
        return Optional.ofNullable(suggests.get(suggestKey(normalizeOwner(ownerId), postId)));
    }

    public Optional<SuggestedPost> removeSuggest(String ownerId, long postId) {
        return Optional.ofNullable(suggests.remove(suggestKey(normalizeOwner(ownerId), postId)));
    }

    public List<SuggestedPost> listSuggests(String ownerId) {
        String owner = normalizeOwner(ownerId);
        return suggests.values().stream()
                .filter(s -> s.ownerId().equals(owner))
                .sorted((a, b) -> Long.compare(b.postId(), a.postId()))
                .toList();
    }

    public Map<String, Object> snapshotSuggests() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (SuggestedPost suggest : suggests.values()) {
            out.computeIfAbsent(suggest.ownerId(), k -> new ArrayList<Map<String, Object>>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) out.get(suggest.ownerId());
            list.add(suggest.toWallItem());
        }
        return out;
    }

    // --- Comments ---

    public EmulatedComment putComment(
            Network network,
            String ownerId,
            String commentId,
            String commentType,
            String text,
            Instant createdAt,
            boolean deleted) {
        String owner = normalizeOwner(ownerId);
        String id = commentId == null || commentId.isBlank() ? String.valueOf(commentSeq.incrementAndGet()) : commentId;
        Instant at = createdAt == null ? Instant.now() : createdAt;
        String type = commentType == null || commentType.isBlank() ? "wall" : commentType;
        EmulatedComment comment = new EmulatedComment(network, owner, id, type, text, at, deleted, false);
        comments.put(commentKey(network, owner, id), comment);
        return comment;
    }

    public void replaceComments(List<Map<String, Object>> items) {
        comments.clear();
        if (items == null) {
            return;
        }
        for (Map<String, Object> item : items) {
            Network network = Network.from(String.valueOf(item.getOrDefault("network", "vk")));
            String ownerId = String.valueOf(item.getOrDefault("ownerId", item.getOrDefault("owner_id", "0")));
            String commentId = item.get("commentId") != null
                    ? String.valueOf(item.get("commentId"))
                    : item.get("comment_id") != null ? String.valueOf(item.get("comment_id")) : null;
            String type = item.get("commentType") != null
                    ? String.valueOf(item.get("commentType"))
                    : item.get("type") != null ? String.valueOf(item.get("type")) : "wall";
            String text = item.get("text") == null ? "" : String.valueOf(item.get("text"));
            Instant createdAt = parseInstant(item.get("createdAt"));
            boolean deleted = Boolean.TRUE.equals(item.get("deleted"));
            putComment(network, ownerId, commentId, type, text, createdAt, deleted);
        }
    }

    public Optional<EmulatedComment> findComment(Network network, String ownerId, String commentId) {
        return Optional.ofNullable(comments.get(commentKey(network, normalizeOwner(ownerId), commentId)));
    }

    public EmulatedComment deleteComment(Network network, String ownerId, String commentId, String commentType) {
        String owner = normalizeOwner(ownerId);
        String key = commentKey(network, owner, commentId);
        EmulatedComment existing = comments.get(key);
        if (existing == null) {
            // Idempotent delete: create as already deleted.
            return putComment(network, owner, commentId, commentType, "", Instant.now(), true);
        }
        EmulatedComment updated = existing.withDeleted(true).withRestored(false);
        comments.put(key, updated);
        return updated;
    }

    public Optional<EmulatedComment> restoreComment(Network network, String ownerId, String commentId) {
        String key = commentKey(network, normalizeOwner(ownerId), commentId);
        EmulatedComment existing = comments.get(key);
        if (existing == null) {
            return Optional.empty();
        }
        if (network == Network.MAX || network == Network.TELEGRAM) {
            return Optional.of(existing); // caller decides not restorable
        }
        EmulatedComment updated = existing.withDeleted(false).withRestored(true);
        comments.put(key, updated);
        return Optional.of(updated);
    }

    public List<EmulatedComment> listComments(Network network, String ownerId) {
        String owner = ownerId == null ? null : normalizeOwner(ownerId);
        return comments.values().stream()
                .filter(c -> network == null || c.network() == network)
                .filter(c -> owner == null || c.ownerId().equals(owner))
                .toList();
    }

    public Map<String, Object> snapshotComments() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (EmulatedComment c : comments.values()) {
            items.add(c.toMap());
        }
        return Map.of("items", items, "count", items.size());
    }

    // --- Bans ---

    public BannedUser ban(Network network, String groupOrChatId, String userId, Map<String, Object> payload) {
        String group = normalizeOwner(groupOrChatId);
        String user = userId == null ? "" : userId;
        Map<String, Object> safe = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    safe.put(entry.getKey(), entry.getValue());
                }
            }
        }
        BannedUser ban = new BannedUser(network, group, user, Instant.now(), Map.copyOf(safe));
        bans.put(banKey(network, group, user), ban);
        return ban;
    }

    public boolean unban(Network network, String groupOrChatId, String userId) {
        return bans.remove(banKey(network, normalizeOwner(groupOrChatId), userId == null ? "" : userId)) != null;
    }

    public boolean isBanned(Network network, String groupOrChatId, String userId) {
        return bans.containsKey(banKey(network, normalizeOwner(groupOrChatId), userId == null ? "" : userId));
    }

    public Map<String, Object> snapshotBans() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (BannedUser ban : bans.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("network", ban.network().id());
            row.put("groupId", ban.groupId());
            row.put("userId", ban.userId());
            row.put("bannedAt", ban.bannedAt().toString());
            row.put("payload", ban.payload());
            items.add(row);
        }
        return Map.of("items", items, "count", items.size());
    }

    private static Instant parseInstant(Object raw) {
        if (raw == null) {
            return Instant.now();
        }
        if (raw instanceof Number n) {
            long v = n.longValue();
            // seconds vs millis heuristic
            return v < 10_000_000_000L ? Instant.ofEpochSecond(v) : Instant.ofEpochMilli(v);
        }
        try {
            return Instant.parse(String.valueOf(raw));
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private static String suggestKey(String ownerId, long postId) {
        return ownerId + "|" + postId;
    }

    private static String commentKey(Network network, String ownerId, String commentId) {
        return network.id() + "|" + ownerId + "|" + commentId;
    }

    private static String banKey(Network network, String groupId, String userId) {
        return network.id() + "|" + groupId + "|" + userId;
    }

    static String normalizeOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return "0";
        }
        String trimmed = ownerId.trim();
        if (trimmed.startsWith("club")) {
            return "-" + trimmed.substring(4);
        }
        return trimmed;
    }

    public record SuggestedPost(String ownerId, long postId, Map<String, Object> fields, Instant createdAt) {
        public Map<String, Object> toWallItem() {
            Map<String, Object> item = new LinkedHashMap<>(fields);
            item.put("id", postId);
            item.put("owner_id", Long.parseLong(ownerId));
            item.putIfAbsent("post_type", "suggest");
            return item;
        }
    }

    public record EmulatedComment(
            Network network,
            String ownerId,
            String commentId,
            String commentType,
            String text,
            Instant createdAt,
            boolean deleted,
            boolean restored) {

        public EmulatedComment withDeleted(boolean value) {
            return new EmulatedComment(network, ownerId, commentId, commentType, text, createdAt, value, restored);
        }

        public EmulatedComment withRestored(boolean value) {
            return new EmulatedComment(network, ownerId, commentId, commentType, text, createdAt, deleted, value);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("network", network.id());
            map.put("ownerId", ownerId);
            map.put("commentId", commentId);
            map.put("commentType", commentType);
            map.put("text", text);
            map.put("createdAt", createdAt.toString());
            map.put("deleted", deleted);
            map.put("restored", restored);
            return map;
        }
    }

    public record BannedUser(
            Network network, String groupId, String userId, Instant bannedAt, Map<String, Object> payload) {}
}
