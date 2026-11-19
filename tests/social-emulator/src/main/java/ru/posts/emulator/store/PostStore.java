package ru.posts.emulator.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PostStore {

    private final ConcurrentMap<String, EmulatedPost> postsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();
    private final ConcurrentMap<Network, AtomicLong> createdCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Network, AtomicLong> duplicateAttemptCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<Network, AtomicLong> remoteIdSequences = new ConcurrentHashMap<>();

    public PostStore() {
        for (Network network : Network.values()) {
            createdCounters.put(network, new AtomicLong());
            duplicateAttemptCounters.put(network, new AtomicLong());
            remoteIdSequences.put(network, new AtomicLong());
        }
    }

    public record CreateResult(EmulatedPost post, boolean created) {}

    public CreateResult createOrGet(
            Network network, String channelId, String idempotencyKey, String text, Map<String, Object> metadata) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String existingId = lookupIdempotency(network, channelId, idempotencyKey);
            if (existingId != null) {
                duplicateAttemptCounters.get(network).incrementAndGet();
                return new CreateResult(postsById.get(existingId), false);
            }
        }
        String key = indexKey(network, channelId, idempotencyKey);

        long remoteSeq = remoteIdSequences.get(network).incrementAndGet();
        String remotePostId =
                switch (network) {
                    case VK -> String.valueOf(remoteSeq);
                    case TELEGRAM -> String.valueOf(remoteSeq);
                    case OK -> "ok-" + remoteSeq;
                    case MAX -> "max-" + remoteSeq;
                };

        Map<String, Object> safeMeta = new LinkedHashMap<>();
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    safeMeta.put(entry.getKey(), entry.getValue());
                }
            }
        }
        EmulatedPost post = new EmulatedPost(
                UUID.randomUUID().toString(),
                network,
                channelId,
                remotePostId,
                blankToNull(idempotencyKey),
                text,
                Instant.now(),
                Map.copyOf(safeMeta));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String raced = idempotencyIndex.putIfAbsent(key, post.id());
            if (raced != null) {
                duplicateAttemptCounters.get(network).incrementAndGet();
                return new CreateResult(postsById.get(raced), false);
            }
        }

        postsById.put(post.id(), post);
        createdCounters.get(network).incrementAndGet();
        return new CreateResult(post, true);
    }

    public Optional<EmulatedPost> findByIdempotencyKey(Network network, String channelId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String id = lookupIdempotency(network, channelId, idempotencyKey);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(postsById.get(id));
    }

    private String lookupIdempotency(Network network, String channelId, String idempotencyKey) {
        String id = idempotencyIndex.get(indexKey(network, channelId, idempotencyKey));
        if (id != null || channelId == null || channelId.isBlank()) {
            return id;
        }
        String alt = channelId.startsWith("-") ? channelId.substring(1) : "-" + channelId;
        return idempotencyIndex.get(indexKey(network, alt, idempotencyKey));
    }

    public List<EmulatedPost> list(Network network, String channelId) {
        return postsById.values().stream()
                .filter(post -> network == null || post.network() == network)
                .filter(post ->
                        channelId == null || channelId.isBlank() || channelIdsEqual(channelId, post.channelId()))
                .sorted(Comparator.comparing(EmulatedPost::createdAt))
                .toList();
    }

    /** VK owner_id may be stored with or without a leading '-'. */
    static boolean channelIdsEqual(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.equals(right)) {
            return true;
        }
        return stripLeadingMinus(left).equals(stripLeadingMinus(right));
    }

    private static String stripLeadingMinus(String value) {
        return value.startsWith("-") ? value.substring(1) : value;
    }

    public Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        long total = 0;
        for (Network network : Network.values()) {
            long count = createdCounters.get(network).get();
            result.put(network.id(), count);
            total += count;
        }
        result.put("total", total);
        return result;
    }

    public Map<String, Long> duplicateAttemptCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Network network : Network.values()) {
            result.put(network.id(), duplicateAttemptCounters.get(network).get());
        }
        return result;
    }

    /**
     * True duplicates: more than one stored post sharing the same network+channel+idempotencyKey.
     * With correct idempotency this list is empty; used to assert zero duplicates.
     */
    public List<DuplicateGroup> findDuplicates() {
        Map<String, List<EmulatedPost>> grouped = postsById.values().stream()
                .filter(post -> post.idempotencyKey() != null)
                .collect(Collectors.groupingBy(
                        post -> indexKey(post.network(), post.channelId(), post.idempotencyKey())));

        List<DuplicateGroup> duplicates = new ArrayList<>();
        for (Map.Entry<String, List<EmulatedPost>> entry : grouped.entrySet()) {
            if (entry.getValue().size() > 1) {
                EmulatedPost sample = entry.getValue().getFirst();
                duplicates.add(new DuplicateGroup(
                        sample.network(),
                        sample.channelId(),
                        sample.idempotencyKey(),
                        entry.getValue().stream()
                                .map(EmulatedPost::remotePostId)
                                .toList(),
                        entry.getValue().size()));
            }
        }
        return duplicates;
    }

    public VerificationReport verify() {
        List<DuplicateGroup> duplicates = findDuplicates();
        Map<Network, Long> byNetwork = new EnumMap<>(Network.class);
        for (Network network : Network.values()) {
            byNetwork.put(network, createdCounters.get(network).get());
        }
        return new VerificationReport(
                postsById.size(),
                byNetwork.entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().id(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)),
                duplicateAttemptCounts(),
                duplicates,
                duplicates.isEmpty());
    }

    public Optional<EmulatedPost> findByRemoteId(Network network, String channelId, String remotePostId) {
        return postsById.values().stream()
                .filter(post -> post.network() == network)
                .filter(post ->
                        channelId == null || channelId.isBlank() || channelIdsEqual(channelId, post.channelId()))
                .filter(post -> remotePostId != null && remotePostId.equals(post.remotePostId()))
                .findFirst();
    }

    /**
     * Seed a post with an explicit remote id and createdAt (for TG 48h delete-window tests).
     */
    public EmulatedPost seed(
            Network network,
            String channelId,
            String remotePostId,
            String text,
            Instant createdAt,
            Map<String, Object> metadata) {
        Map<String, Object> safeMeta = new LinkedHashMap<>();
        if (metadata != null) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    safeMeta.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Instant at = createdAt == null ? Instant.now() : createdAt;
        String remote = remotePostId == null || remotePostId.isBlank()
                ? String.valueOf(remoteIdSequences.get(network).incrementAndGet())
                : remotePostId;
        EmulatedPost post = new EmulatedPost(
                UUID.randomUUID().toString(),
                network,
                channelId == null ? "0" : channelId,
                remote,
                null,
                text,
                at,
                Map.copyOf(safeMeta));
        // Replace any existing remote id match so re-seed is idempotent for tests.
        findByRemoteId(network, channelId, remote).ifPresent(existing -> {
            postsById.remove(existing.id());
            if (existing.idempotencyKey() != null) {
                idempotencyIndex.remove(indexKey(network, existing.channelId(), existing.idempotencyKey()));
            }
        });
        postsById.put(post.id(), post);
        createdCounters.get(network).incrementAndGet();
        return post;
    }

    public boolean deleteByRemoteId(Network network, String channelId, String remotePostId) {
        Optional<EmulatedPost> found = findByRemoteId(network, channelId, remotePostId);
        if (found.isEmpty()) {
            return false;
        }
        EmulatedPost post = found.get();
        postsById.remove(post.id());
        if (post.idempotencyKey() != null) {
            idempotencyIndex.remove(indexKey(network, post.channelId(), post.idempotencyKey()));
        }
        return true;
    }

    public boolean markPinned(Network network, String channelId, String remotePostId, boolean pinned) {
        Optional<EmulatedPost> found = postsById.values().stream()
                .filter(post -> post.network() == network)
                .filter(post -> channelId == null || channelId.equals(post.channelId()))
                .filter(post -> remotePostId != null && remotePostId.equals(post.remotePostId()))
                .findFirst();
        if (found.isEmpty()) {
            return false;
        }
        EmulatedPost post = found.get();
        Map<String, Object> meta = new LinkedHashMap<>(post.metadata());
        meta.put("pinned", pinned);
        EmulatedPost updated = new EmulatedPost(
                post.id(),
                post.network(),
                post.channelId(),
                post.remotePostId(),
                post.idempotencyKey(),
                post.text(),
                post.createdAt(),
                Map.copyOf(meta));
        postsById.put(post.id(), updated);
        return true;
    }

    public void reset() {
        postsById.clear();
        idempotencyIndex.clear();
        for (Network network : Network.values()) {
            createdCounters.get(network).set(0);
            duplicateAttemptCounters.get(network).set(0);
            remoteIdSequences.get(network).set(0);
        }
    }

    private static String indexKey(Network network, String channelId, String idempotencyKey) {
        return network.id() + "|" + nullToEmpty(channelId) + "|" + idempotencyKey;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record DuplicateGroup(
            Network network, String channelId, String idempotencyKey, List<String> remotePostIds, int count) {}

    public record VerificationReport(
            long totalPosts,
            Map<String, Long> postsByNetwork,
            Map<String, Long> duplicateAttemptsByNetwork,
            List<DuplicateGroup> trueDuplicates,
            boolean ok) {}
}
