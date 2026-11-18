package ru.posts.api.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.api.error.ApiException;
import ru.posts.api.error.ProblemCodes;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.common.CursorPage;
import ru.posts.common.uuid.UuidV7;
import ru.posts.messaging.topology.ActionsTopology;
import ru.posts.messaging.topology.IngestTopology;
import ru.posts.messaging.topology.MediaTopology;
import ru.posts.messaging.topology.NotifyTopology;
import ru.posts.messaging.topology.PublishTopology;
import ru.posts.persistence.entity.AdminIdempotencyEntity;
import ru.posts.persistence.repository.AdminIdempotencyRepository;

/** RabbitMQ-backed queue admin. Registered by {@code AdminQueueConfig} when AMQP is available. */
public class RabbitAdminQueueService implements AdminQueueService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final AdminIdempotencyRepository adminIdempotencyRepository;

    public RabbitAdminQueueService(
            RabbitTemplate rabbitTemplate,
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
            AdminIdempotencyRepository adminIdempotencyRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
        this.adminIdempotencyRepository = adminIdempotencyRepository;
    }

    @Override
    public List<AdminDtos.QueueInfo> listQueues() {
        List<AdminDtos.QueueInfo> out = new ArrayList<>();
        for (String name : knownQueues()) {
            var props = rabbitAdmin.getQueueProperties(name);
            long messages = props == null ? 0L : ((Number) props.getOrDefault("QUEUE_MESSAGE_COUNT", 0)).longValue();
            long consumers = props == null ? 0L : ((Number) props.getOrDefault("QUEUE_CONSUMER_COUNT", 0)).longValue();
            out.add(new AdminDtos.QueueInfo(name, name.endsWith(".dlq"), messages, consumers));
        }
        return out;
    }

    @Override
    public CursorPage.AdminQueueMessagePage listMessages(String queue, CursorPage.Params page) {
        requireKnownQueue(queue);
        int limit = page.limitOrDefault();
        List<AdminDtos.QueueMessage> items = new ArrayList<>();
        List<Message> buffer = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Message message = rabbitTemplate.receive(queue, 100);
            if (message == null) {
                break;
            }
            buffer.add(message);
            items.add(toDto(message));
        }
        for (Message message : buffer) {
            rabbitTemplate.send(queue, message);
        }
        return new CursorPage.AdminQueueMessagePage(items, null, false);
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
        requireKnownQueue(queue);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED, "Idempotency-Key required");
        }
        String scope = "admin.queue." + action + ":" + queue + ":" + messageId;
        String hash = sha256(action + "|" + queue + "|" + messageId);
        var existing = adminIdempotencyRepository.findByScopeAndIdempotencyKey(scope, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) {
                throw new ApiException(
                        HttpStatus.CONFLICT, ProblemCodes.IDEMPOTENCY_CONFLICT, "Idempotency-Key reused");
            }
            return new AdminDtos.QueueMessageActionResponse(queue, messageId, action, false, "idempotent_replay");
        }

        Message target = findAndRemove(queue, messageId);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Message not found in queue");
        }
        if (requeue) {
            String liveQueue = queue.endsWith(".dlq") ? queue.substring(0, queue.length() - 4) : queue;
            rabbitTemplate.send(liveQueue, target);
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

    private Message findAndRemove(String queue, String messageId) {
        List<Message> buffer = new ArrayList<>();
        Message found = null;
        for (int i = 0; i < 500; i++) {
            Message message = rabbitTemplate.receive(queue, 50);
            if (message == null) {
                break;
            }
            String id = messageIdOf(message);
            if (found == null && messageId.equals(id)) {
                found = message;
            } else {
                buffer.add(message);
            }
        }
        for (Message message : buffer) {
            rabbitTemplate.send(queue, message);
        }
        return found;
    }

    private static AdminDtos.QueueMessage toDto(Message message) {
        MessageProperties props = message.getMessageProperties();
        Map<String, Object> headers = new LinkedHashMap<>();
        if (props.getHeaders() != null) {
            headers.putAll(props.getHeaders());
        }
        Integer redelivered = null;
        Object deliveryCount =
                props.getHeaders() == null ? null : props.getHeaders().get("x-delivery-count");
        if (deliveryCount instanceof Number number) {
            redelivered = number.intValue();
        }
        Instant enqueued =
                props.getTimestamp() == null ? null : props.getTimestamp().toInstant();
        return new AdminDtos.QueueMessage(
                messageIdOf(message),
                new String(message.getBody(), StandardCharsets.UTF_8),
                headers,
                redelivered,
                enqueued);
    }

    private static String messageIdOf(Message message) {
        String id = message.getMessageProperties().getMessageId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        Object headerId = message.getMessageProperties().getHeaders().get("message_id");
        if (headerId != null) {
            return String.valueOf(headerId);
        }
        return sha256Bytes(message.getBody());
    }

    private void requireKnownQueue(String queue) {
        if (!knownQueues().contains(queue)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ProblemCodes.NOT_FOUND, "Unknown queue");
        }
    }

    private static List<String> knownQueues() {
        List<String> names = new ArrayList<>();
        for (String network : PublishTopology.NETWORKS) {
            names.add(PublishTopology.queueName(network));
            names.add(PublishTopology.dlqName(network));
            names.add(ActionsTopology.queueName(network));
            names.add(ActionsTopology.dlqName(network));
        }
        names.add(MediaTopology.QUEUE_PROCESS);
        names.add(MediaTopology.QUEUE_PROCESS_DLQ);
        names.add(IngestTopology.GRABBER_QUEUE);
        names.add(IngestTopology.GRABBER_DLQ);
        names.add(NotifyTopology.EVENTS_QUEUE);
        names.add(NotifyTopology.EVENTS_DLQ);
        names.add(NotifyTopology.EMAIL_QUEUE);
        names.add(NotifyTopology.EMAIL_DLQ);
        return names;
    }

    private static String sha256(String raw) {
        return sha256Bytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Bytes(byte[] raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
