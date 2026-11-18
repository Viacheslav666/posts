package ru.posts.api.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.persistence.entity.AdminIdempotencyEntity;
import ru.posts.persistence.repository.AdminIdempotencyRepository;

/**
 * Default queue admin when RabbitMQ is not configured (tests / environments without AMQP). Supports
 * seeding messages for IT coverage of retry idempotency. Registered by {@code AdminQueueConfig}.
 */
public class InMemoryAdminQueueService implements AdminQueueService {

    private final ConcurrentMap<String, List<StoredMessage>> queues = new ConcurrentHashMap<>();
    private final AdminIdempotencyRepository adminIdempotencyRepository;

    public InMemoryAdminQueueService(AdminIdempotencyRepository adminIdempotencyRepository) {
        this.adminIdempotencyRepository = adminIdempotencyRepository;
        for (String network : PublishTopology.NETWORKS) {
            queues.put(PublishTopology.queueName(network), new ArrayList<>());
            queues.put(PublishTopology.dlqName(network), new ArrayList<>());
        }
    }

    public void seed(String queue, String messageId, String payload) {
        queues.computeIfAbsent(queue, ignored -> new ArrayList<>())
                .add(new StoredMessage(messageId, payload, Map.of(), Instant.now(), 0));
    }

    @Override
    public List<AdminDtos.QueueInfo> listQueues() {
        List<AdminDtos.QueueInfo> out = new ArrayList<>();
        for (Map.Entry<String, List<StoredMessage>> e : queues.entrySet()) {
            out.add(new AdminDtos.QueueInfo(
                    e.getKey(), e.getKey().endsWith(".dlq"), e.getValue().size(), 0));
        }
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    @Override
    public CursorPage.AdminQueueMessagePage listMessages(String queue, CursorPage.Params page) {
        List<StoredMessage> messages = new ArrayList<>(queues.getOrDefault(queue, List.of()));
        if (page.cursor() != null && !page.cursor().isBlank()) {
            String cursor = page.cursor();
            messages = messages.stream()
                    .dropWhile(m -> !m.messageId().equals(cursor))
                    .skip(1)
                    .toList();
        }
        int limit = page.limitOrDefault();
        boolean hasMore = messages.size() > limit;
        List<StoredMessage> pageItems = hasMore ? messages.subList(0, limit) : messages;
        String next = hasMore && !pageItems.isEmpty()
                ? pageItems.get(pageItems.size() - 1).messageId()
                : null;
        List<AdminDtos.QueueMessage> items = pageItems.stream()
                .map(m -> new AdminDtos.QueueMessage(
                        m.messageId(), m.payload(), m.headers(), m.redeliveredCount(), m.enqueuedAt()))
                .toList();
        return new CursorPage.AdminQueueMessagePage(items, next, hasMore);
    }

    @Override
    @Transactional
    public AdminDtos.QueueMessageActionResponse retry(
            String queue, String messageId, String idempotencyKey, UUID actorId) {
        return mutate(queue, messageId, idempotencyKey, actorId, "retry", true);
    }

    @Override
    @Transactional
    public AdminDtos.QueueMessageActionResponse delete(
            String queue, String messageId, String idempotencyKey, UUID actorId) {
        return mutate(queue, messageId, idempotencyKey, actorId, "delete", false);
    }

    private AdminDtos.QueueMessageActionResponse mutate(
            String queue, String messageId, String idempotencyKey, UUID actorId, String action, boolean requeue) {
        requireIdempotencyKey(idempotencyKey);
        String scope = "admin.queue." + action + ":" + queue + ":" + messageId;
        String hash = sha256(action + "|" + queue + "|" + messageId);
        var existing = adminIdempotencyRepository.findByScopeAndIdempotencyKey(scope, idempotencyKey);
        if (existing.isPresent()) {
            AdminIdempotencyEntity row = existing.get();
            if (!row.getRequestHash().equals(hash)) {
                throw new ApiException(
                        HttpStatus.CONFLICT, ProblemCodes.IDEMPOTENCY_CONFLICT, "Idempotency-Key reused");
            }
            return new AdminDtos.QueueMessageActionResponse(queue, messageId, action, false, "idempotent_replay");
        }

        List<StoredMessage> list = queues.getOrDefault(queue, List.of());
        StoredMessage found = null;
        synchronized (queues) {
            list = queues.computeIfAbsent(queue, ignored -> new ArrayList<>());
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).messageId().equals(messageId)) {
                    found = list.remove(i);
                    break;
                }
            }
            if (found != null && requeue) {
                String target = queue.endsWith(".dlq") ? queue.substring(0, queue.length() - 4) : queue;
                queues.computeIfAbsent(target, ignored -> new ArrayList<>())
                        .add(new StoredMessage(
                                found.messageId(),
                                found.payload(),
                                found.headers(),
                                Instant.now(),
                                found.redeliveredCount() + 1));
            }
        }
        if (found == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Message not found in queue");
        }

        AdminIdempotencyEntity row = new AdminIdempotencyEntity();
        row.setId(UuidV7.generate());
        row.setActorUserId(actorId);
        row.setScope(scope);
        row.setIdempotencyKey(idempotencyKey);
        row.setRequestHash(hash);
        row.setResponseBody(Map.of("action", action, "applied", true));
        row.setStatusCode(200);
        adminIdempotencyRepository.save(row);

        return new AdminDtos.QueueMessageActionResponse(
                queue, messageId, action, true, requeue ? "requeued" : "deleted");
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Idempotency-Key required");
        }
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record StoredMessage(
            String messageId, String payload, Map<String, Object> headers, Instant enqueuedAt, int redeliveredCount) {}
}
