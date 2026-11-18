package ru.posts.api.admin;

import java.util.List;
import ru.posts.api.web.dto.admin.AdminDtos;
import ru.posts.api.web.dto.common.CursorPage;

/** Platform-admin view of RabbitMQ queues / DLQ with idempotent retry & delete. */
public interface AdminQueueService {

    List<AdminDtos.QueueInfo> listQueues();

    CursorPage.AdminQueueMessagePage listMessages(String queue, CursorPage.Params page);

    AdminDtos.QueueMessageActionResponse retry(
            String queue, String messageId, String idempotencyKey, java.util.UUID actorId);

    AdminDtos.QueueMessageActionResponse delete(
            String queue, String messageId, String idempotencyKey, java.util.UUID actorId);
}
