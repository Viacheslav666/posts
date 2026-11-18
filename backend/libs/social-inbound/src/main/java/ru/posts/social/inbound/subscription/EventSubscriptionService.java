package ru.posts.social.inbound.subscription;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.ChannelEventSubscriptionEntity;
import ru.posts.persistence.entity.TelegramBotEntity;
import ru.posts.persistence.repository.ChannelEventSubscriptionRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.TelegramBotRepository;
import ru.posts.social.inbound.client.MaxSubscriptionClient;
import ru.posts.social.inbound.client.TelegramWebhookClient;
import ru.posts.social.inbound.client.VkCallbackClient;
import ru.posts.social.inbound.config.SocialInboundProperties;

/**
 * Ensures / releases per-channel inbound event subscriptions. Idempotent; optimistic locking via
 * {@code version}.
 */
public class EventSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(EventSubscriptionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final List<String> TG_ALLOWED_UPDATES = List.of(
            "channel_post", "edited_channel_post", "message", "edited_message", "my_chat_member", "chat_member");

    private final ChannelRepository channelRepository;
    private final ChannelEventSubscriptionRepository subscriptionRepository;
    private final TelegramBotRepository telegramBotRepository;
    private final SecretCrypto secretCrypto;
    private final TokenResolver tokenResolver;
    private final SocialInboundProperties properties;
    private final VkCallbackClient vkCallbackClient;
    private final TelegramWebhookClient telegramWebhookClient;
    private final MaxSubscriptionClient maxSubscriptionClient;

    public EventSubscriptionService(
            ChannelRepository channelRepository,
            ChannelEventSubscriptionRepository subscriptionRepository,
            TelegramBotRepository telegramBotRepository,
            SecretCrypto secretCrypto,
            TokenResolver tokenResolver,
            SocialInboundProperties properties,
            VkCallbackClient vkCallbackClient,
            TelegramWebhookClient telegramWebhookClient,
            MaxSubscriptionClient maxSubscriptionClient) {
        this.channelRepository = channelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.telegramBotRepository = telegramBotRepository;
        this.secretCrypto = secretCrypto;
        this.tokenResolver = tokenResolver;
        this.properties = properties;
        this.vkCallbackClient = vkCallbackClient;
        this.telegramWebhookClient = telegramWebhookClient;
        this.maxSubscriptionClient = maxSubscriptionClient;
    }

    @Transactional
    public ChannelEventSubscriptionEntity ensure(UUID channelId, String consumer, List<String> eventTypes) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return ensureOnce(channelId, consumer, eventTypes);
            } catch (OptimisticLockingFailureException ex) {
                log.debug("ensure optimistic retry channelId={} attempt={}", channelId, attempt);
            }
        }
        throw new IllegalStateException("Failed to ensure subscription after retries: " + channelId);
    }

    @Transactional
    public void release(UUID channelId, String consumer) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                releaseOnce(channelId, consumer);
                return;
            } catch (OptimisticLockingFailureException ex) {
                log.debug("release optimistic retry channelId={} attempt={}", channelId, attempt);
            }
        }
        throw new IllegalStateException("Failed to release subscription after retries: " + channelId);
    }

    @Transactional(readOnly = true)
    public ChannelEventSubscriptionEntity getRequired(UUID channelId) {
        return subscriptionRepository
                .findByChannelId(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ChannelEventSubscriptionEntity> find(UUID channelId) {
        return subscriptionRepository.findByChannelId(channelId);
    }

    /** Repair / re-register remote subscription (API + health job). */
    @Transactional
    public ChannelEventSubscriptionEntity repair(UUID channelId) {
        ChannelEventSubscriptionEntity sub = getRequired(channelId);
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found"));
        registerRemote(channel, sub);
        sub.setStatus("active");
        sub.setLastError(null);
        sub.setFailureCount(0);
        sub.setUpdatedAt(Instant.now());
        return subscriptionRepository.save(sub);
    }

    private ChannelEventSubscriptionEntity ensureOnce(UUID channelId, String consumer, List<String> eventTypes) {
        ChannelEntity channel = channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found"));
        ChannelEventSubscriptionEntity sub =
                subscriptionRepository.findByChannelId(channelId).orElseGet(() -> newSubscription(channel));

        Set<String> consumers = new LinkedHashSet<>(sub.getConsumers());
        consumers.add(consumer);
        sub.setConsumers(List.copyOf(consumers));

        Set<String> types = new LinkedHashSet<>(sub.getEventTypes());
        if (eventTypes != null) {
            types.addAll(eventTypes);
        }
        sub.setEventTypes(List.copyOf(types));
        sub.setUpdatedAt(Instant.now());

        if (sub.getSecretEnc() == null) {
            String secret = randomSecret(channel.getNetwork());
            sub.setSecretEnc(secretCrypto.seal(channel.getWorkspaceId(), secret));
        }

        registerRemote(channel, sub);
        if (!"active".equals(sub.getStatus()) && !"degraded".equals(sub.getStatus())) {
            sub.setStatus("active");
        }
        return subscriptionRepository.save(sub);
    }

    private void releaseOnce(UUID channelId, String consumer) {
        ChannelEventSubscriptionEntity sub =
                subscriptionRepository.findByChannelId(channelId).orElse(null);
        if (sub == null) {
            return;
        }
        Set<String> consumers = new LinkedHashSet<>(sub.getConsumers());
        consumers.remove(consumer);
        sub.setConsumers(List.copyOf(consumers));
        sub.setUpdatedAt(Instant.now());
        if (consumers.isEmpty()) {
            ChannelEntity channel =
                    channelRepository.findByIdAndDeletedAtIsNull(channelId).orElse(null);
            if (channel != null) {
                unregisterRemote(channel, sub);
            }
            sub.setStatus("disabled");
        }
        subscriptionRepository.save(sub);
    }

    private ChannelEventSubscriptionEntity newSubscription(ChannelEntity channel) {
        ChannelEventSubscriptionEntity sub = new ChannelEventSubscriptionEntity();
        sub.setWorkspaceId(channel.getWorkspaceId());
        sub.setChannelId(channel.getId());
        sub.setNetwork(channel.getNetwork());
        sub.setMode(modeFor(channel));
        sub.setEventTypes(List.of());
        sub.setConsumers(List.of());
        sub.setStatus("pending");
        sub.setCreatedAt(Instant.now());
        sub.setUpdatedAt(Instant.now());
        return sub;
    }

    private String modeFor(ChannelEntity channel) {
        return switch (channel.getNetwork()) {
            case "vk" -> "vk_callback";
            case "tg" -> channel.getBotTokenEnc() != null && channel.getBotTokenEnc().length > 1
                    ? "tg_custom_bot"
                    : "tg_platform_bot";
            case "max" -> "max_webhook";
            default -> throw new IllegalArgumentException("Unsupported network: " + channel.getNetwork());
        };
    }

    private void registerRemote(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        String secret = secretCrypto.unseal(channel.getWorkspaceId(), sub.getSecretEnc());
        switch (channel.getNetwork()) {
            case "vk" -> registerVk(channel, sub, secret);
            case "tg" -> registerTg(channel, sub, secret);
            case "max" -> registerMax(channel, sub, secret);
            default -> throw new IllegalArgumentException("Unsupported network: " + channel.getNetwork());
        }
    }

    private void unregisterRemote(ChannelEntity channel, ChannelEventSubscriptionEntity sub) {
        try {
            switch (channel.getNetwork()) {
                case "vk" -> {
                    if (sub.getRemoteServerId() != null) {
                        String token = tokenResolver.groupToken(channel);
                        vkCallbackClient.deleteCallbackServer(channel.getExternalId(), sub.getRemoteServerId(), token);
                        sub.setRemoteServerId(null);
                    }
                }
                case "tg" -> {
                    // Keep platform bot webhook if other channels still use the bot.
                    if ("tg_custom_bot".equals(sub.getMode())) {
                        TelegramBotEntity bot = telegramBotRepository
                                .findByChannelId(channel.getId())
                                .orElse(null);
                        if (bot != null) {
                            String token = tokenResolver.botToken(bot);
                            telegramWebhookClient.deleteWebhook(token);
                        }
                    }
                }
                case "max" -> {
                    String botToken = tokenResolver.channelBotToken(channel);
                    if (botToken == null) {
                        botToken = tokenResolver.groupToken(channel);
                    }
                    if (botToken != null) {
                        String url = properties.trimmedPublicBaseUrl() + "/hooks/max/" + channel.getId();
                        maxSubscriptionClient.unsubscribe(botToken, url);
                    }
                }
                default -> {
                    // no-op
                }
            }
        } catch (RuntimeException ex) {
            log.warn(
                    "unregister_remote_failed channelId={} network={} err={}",
                    channel.getId(),
                    channel.getNetwork(),
                    ex.toString());
            sub.setLastError(ex.getMessage());
        }
    }

    private void registerVk(ChannelEntity channel, ChannelEventSubscriptionEntity sub, String secret) {
        String token = tokenResolver.groupToken(channel);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("VK group token missing for channel " + channel.getId());
        }
        String url = properties.trimmedPublicBaseUrl() + "/hooks/vk/" + channel.getId();
        List<VkCallbackClient.CallbackServer> servers =
                vkCallbackClient.getCallbackServers(channel.getExternalId(), token);
        String serverId = servers.stream()
                .filter(s -> url.equals(s.url()))
                .map(VkCallbackClient.CallbackServer::id)
                .findFirst()
                .orElse(null);
        if (serverId == null) {
            String code = vkCallbackClient.getCallbackConfirmationCode(channel.getExternalId(), token);
            sub.setConfirmationCode(code);
            serverId = vkCallbackClient.addCallbackServer(channel.getExternalId(), url, "Posts", secret, token);
        }
        sub.setRemoteServerId(serverId);
        Map<String, String> settings = new LinkedHashMap<>();
        for (String type : sub.getEventTypes()) {
            settings.put(type, "1");
        }
        if (settings.isEmpty()) {
            settings.put("wall_post_new", "1");
            settings.put("wall_reply_new", "1");
            settings.put("message_new", "1");
        }
        vkCallbackClient.setCallbackSettings(channel.getExternalId(), serverId, settings, token);
    }

    private void registerTg(ChannelEntity channel, ChannelEventSubscriptionEntity sub, String secret) {
        TelegramBotEntity bot;
        String token;
        String webhookSecret = secret;
        if ("tg_custom_bot".equals(sub.getMode())) {
            final String customSecret = secret;
            bot = telegramBotRepository
                    .findByChannelId(channel.getId())
                    .orElseGet(() -> createCustomBot(channel, customSecret));
            if (!customSecret.equals(bot.getWebhookSecret())) {
                bot.setWebhookSecret(customSecret);
                telegramBotRepository.save(bot);
            }
            token = tokenResolver.botToken(bot);
            if (token == null) {
                token = tokenResolver.channelBotToken(channel);
            }
        } else {
            bot = channel.getBotId() == null
                    ? telegramBotRepository.findByStatusOrderByCreatedAtAsc("active").stream()
                            .filter(b -> "platform".equals(b.getKind()) || b.getKind() == null)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No platform Telegram bot"))
                    : telegramBotRepository
                            .findById(channel.getBotId())
                            .orElseThrow(() -> new IllegalStateException("Telegram bot not found"));
            token = tokenResolver.botToken(bot);
            if (token == null) {
                // local seed: plaintext from env is resolved by api TokenResolver
                throw new IllegalStateException("Telegram bot token missing");
            }
            // ensure webhook secret matches bot row for platform bots
            webhookSecret = bot.getWebhookSecret();
            sub.setSecretEnc(secretCrypto.seal(channel.getWorkspaceId(), webhookSecret));
        }
        String url = properties.trimmedPublicBaseUrl() + "/hooks/tg/" + bot.getId();
        telegramWebhookClient.setWebhook(token, url, webhookSecret, mergeAllowedUpdates(sub.getEventTypes()));
        sub.setRemoteServerId(bot.getId().toString());
    }

    private TelegramBotEntity createCustomBot(ChannelEntity channel, String secret) {
        TelegramBotEntity bot = new TelegramBotEntity();
        bot.setUsername("custom-" + channel.getId().toString().substring(0, 8));
        byte[] enc = channel.getBotTokenEnc();
        bot.setTokenEnc(enc == null ? new byte[] {0} : enc);
        bot.setWebhookSecret(secret);
        bot.setStatus("active");
        bot.setKind("custom");
        bot.setWorkspaceId(channel.getWorkspaceId());
        bot.setChannelId(channel.getId());
        bot.setCreatedAt(Instant.now());
        return telegramBotRepository.save(bot);
    }

    private void registerMax(ChannelEntity channel, ChannelEventSubscriptionEntity sub, String secret) {
        String token = tokenResolver.channelBotToken(channel);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Max bot token missing for channel " + channel.getId());
        }
        String url = properties.trimmedPublicBaseUrl() + "/hooks/max/" + channel.getId();
        maxSubscriptionClient.subscribe(token, url, secret, sub.getEventTypes());
        sub.setRemoteServerId(url);
    }

    private static List<String> mergeAllowedUpdates(List<String> eventTypes) {
        Set<String> set = new LinkedHashSet<>(TG_ALLOWED_UPDATES);
        if (eventTypes != null) {
            set.addAll(eventTypes);
        }
        return new ArrayList<>(set);
    }

    private static String randomSecret(String network) {
        if ("max".equals(network)) {
            // Max: 5–256 chars [A-Za-z0-9_-]
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        // VK secret_key >= 32
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
