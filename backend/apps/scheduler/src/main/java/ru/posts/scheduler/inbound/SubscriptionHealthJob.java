package ru.posts.scheduler.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelEventSubscriptionEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelEventSubscriptionRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
import ru.posts.social.inbound.client.MaxSubscriptionClient;
import ru.posts.social.inbound.client.TelegramWebhookClient;
import ru.posts.social.inbound.client.VkCallbackClient;
import ru.posts.social.inbound.subscription.EventSubscriptionService;
import ru.posts.social.inbound.subscription.SubscriptionNotifier;
import ru.posts.social.inbound.subscription.TokenResolver;

@Component
@ConditionalOnBean(EventSubscriptionService.class)
public class SubscriptionHealthJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionHealthJob.class);
    private static final List<String> ACTIVE_STATUSES = List.of("active", "degraded", "pending", "failed");

    private final ChannelEventSubscriptionRepository subscriptionRepository;
    private final ChannelRepository channelRepository;
    private final TelegramBotRepository telegramBotRepository;
    private final EventSubscriptionService subscriptionService;
    private final TokenResolver tokenResolver;
    private final VkCallbackClient vkCallbackClient;
    private final TelegramWebhookClient telegramWebhookClient;
    private final MaxSubscriptionClient maxSubscriptionClient;
    private final SubscriptionNotifier notifier;

    public SubscriptionHealthJob(
            ChannelEventSubscriptionRepository subscriptionRepository,
            ChannelRepository channelRepository,
            TelegramBotRepository telegramBotRepository,
            EventSubscriptionService subscriptionService,
            TokenResolver tokenResolver,
            VkCallbackClient vkCallbackClient,
            TelegramWebhookClient telegramWebhookClient,
            MaxSubscriptionClient maxSubscriptionClient,
            SubscriptionNotifier notifier) {
        this.subscriptionRepository = subscriptionRepository;
        this.channelRepository = channelRepository;
        this.telegramBotRepository = telegramBotRepository;
        this.subscriptionService = subscriptionService;
        this.tokenResolver = tokenResolver;
        this.vkCallbackClient = vkCallbackClient;
        this.telegramWebhookClient = telegramWebhookClient;
        this.maxSubscriptionClient = maxSubscriptionClient;
        this.notifier = notifier;
    }

    @Scheduled(fixedDelayString = "${posts.social-inbound.health-interval-ms:300000}")
    @SchedulerLock(name = "subscriptionHealth", lockAtMostFor = "10m", lockAtLeastFor = "30s")
    public void run() {
        List<ChannelEventSubscriptionEntity> subs = subscriptionRepository.findByStatusIn(ACTIVE_STATUSES);
        for (ChannelEventSubscriptionEntity sub : subs) {
            if (!dueForRepair(sub.getStatus(), sub.getFailureCount(), sub.getUpdatedAt(), Instant.now())) {
                continue;
            }
            try {
                checkOne(sub);
            } catch (RuntimeException ex) {
                log.warn("subscription_health_failed channelId={} err={}", sub.getChannelId(), ex.toString());
                markFailed(sub, ex.getMessage());
            }
        }
    }

    /**
     * Failed subscriptions are retried with exponential backoff (5 min, doubled, capped at 6 h).
     * Active, degraded and pending subscriptions are always checked.
     */
    static boolean dueForRepair(String status, int failureCount, Instant updatedAt, Instant now) {
        if (!"failed".equals(status)) {
            return true;
        }
        int steps = Math.max(0, Math.min(failureCount - 1, 6));
        long minutes = Math.min(360, 5L << steps);
        Instant updated = updatedAt == null ? Instant.EPOCH : updatedAt;
        return !updated.plus(minutes, ChronoUnit.MINUTES).isAfter(now);
    }

    private void checkOne(ChannelEventSubscriptionEntity sub) {
        ChannelEntity channel =
                channelRepository.findByIdAndDeletedAtIsNull(sub.getChannelId()).orElse(null);
        if (channel == null) {
            return;
        }
        switch (sub.getNetwork()) {
            case "vk" -> checkVk(channel, sub);
            case "tg" -> checkTg(channel, sub);
            case "max" -> checkMax(channel, sub);
            default -> {
                // ignore
            }
        }
    }

    private void checkVk(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        String token = tokenResolver.groupToken(channel);
        List<VkCallbackClient.CallbackServer> servers =
                vkCallbackClient.getCallbackServers(channel.getExternalId(), token);
        boolean ok = sub.getRemoteServerId() != null
                && servers.stream()
                        .anyMatch(s ->
                                sub.getRemoteServerId().equals(s.id()) && !"unconfirmed".equalsIgnoreCase(s.status()));
        if (!ok) {
            degradeAndRepair(channel, sub, "vk_callback_server_missing_or_bad");
        } else {
            clearFailure(sub);
        }
    }

    private void checkTg(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        TelegramBotEntity bot = resolveBot(channel, sub);
        if (bot == null) {
            degradeAndRepair(channel, sub, "tg_bot_missing");
            return;
        }
        String token = tokenResolver.botToken(bot);
        JsonNode info = telegramWebhookClient.getWebhookInfo(token);
        JsonNode result = info == null ? null : info.path("result");
        String lastError =
                result == null ? null : result.path("last_error_message").asText(null);
        int pending = result == null ? 0 : result.path("pending_update_count").asInt(0);
        // gap > 24h: if last_event_at older than 24h and pending > 0
        if (lastError != null && !lastError.isBlank()) {
            degradeAndRepair(channel, sub, "tg_webhook_error: " + lastError);
            return;
        }
        if (sub.getLastEventAt() != null
                && sub.getLastEventAt().isBefore(Instant.now().minusSeconds(24 * 3600))
                && pending > 0) {
            sub.setStatus("degraded");
            sub.setLastError("gap");
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
            notifier.notifyDegraded(
                    channel.getWorkspaceId(),
                    channel.getId(),
                    "tg",
                    "Telegram update gap > 24h",
                    Map.of("pendingUpdateCount", pending));
            subscriptionService.repair(channel.getId());
            return;
        }
        clearFailure(sub);
    }

    private void checkMax(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        String token = tokenResolver.channelBotToken(channel);
        JsonNode body = maxSubscriptionClient.listSubscriptions(token);
        boolean found = false;
        if (body != null) {
            JsonNode subscriptions = body.path("subscriptions");
            if (!subscriptions.isArray()) {
                subscriptions = body.path("result");
            }
            if (subscriptions.isArray()) {
                for (JsonNode s : subscriptions) {
                    if (sub.getRemoteServerId() != null
                            && sub.getRemoteServerId().equals(s.path("url").asText())) {
                        found = true;
                        break;
                    }
                }
            }
        }
        if (!found) {
            // Max auto-unsubscribes after 8h of failures — re-subscribe
            degradeAndRepair(channel, sub, "max_subscription_missing");
        } else {
            clearFailure(sub);
        }
    }

    private TelegramBotEntity resolveBot(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        if (sub.getRemoteServerId() != null) {
            try {
                return telegramBotRepository
                        .findById(UUID.fromString(sub.getRemoteServerId()))
                        .orElse(null);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (channel.getBotId() != null) {
            return telegramBotRepository.findById(channel.getBotId()).orElse(null);
        }
        return telegramBotRepository.findByChannelId(channel.getId()).orElse(null);
    }

    private void degradeAndRepair(ChannelEntity channel, ChannelEventSubscriptionEntity sub, String reason) {
        sub.setStatus("degraded");
        sub.setLastError(reason);
        sub.setFailureCount(sub.getFailureCount() + 1);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
        notifier.notifyDegraded(channel.getWorkspaceId(), channel.getId(), sub.getNetwork(), reason, Map.of());
        try {
            subscriptionService.repair(channel.getId());
        } catch (RuntimeException ex) {
            markFailed(sub, ex.getMessage());
        }
    }

    private void clearFailure(ChannelEventSubscriptionEntity sub) {
        if (sub.getFailureCount() > 0 || !"active".equals(sub.getStatus())) {
            sub.setStatus("active");
            sub.setFailureCount(0);
            sub.setLastError(null);
            sub.setUpdatedAt(Instant.now());
            subscriptionRepository.save(sub);
        }
    }

    private void markFailed(ChannelEventSubscriptionEntity sub, String message) {
        sub.setStatus("failed");
        sub.setLastError(message);
        sub.setFailureCount(sub.getFailureCount() + 1);
        sub.setUpdatedAt(Instant.now());
        subscriptionRepository.save(sub);
    }
}
