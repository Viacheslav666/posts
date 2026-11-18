package ru.posts.api.billing;

import java.util.UUID;

public record YooKassaWebhookReceivedEvent(UUID inboxId) {}
