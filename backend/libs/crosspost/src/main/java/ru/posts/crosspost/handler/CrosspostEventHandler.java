package ru.posts.crosspost.handler;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.common.uuid.UuidV7;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.content.hash.SimHasher;
import ru.posts.crosspost.config.CrosspostProperties;
import ru.posts.crosspost.loop.LoopGuard;
import ru.posts.crosspost.metrics.CrosspostMetrics;
import ru.posts.crosspost.model.CrosspostCandidate;
import ru.posts.crosspost.model.InboundCandidateFactory;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.social.inbound.HandlerContext;
import ru.posts.social.inbound.HandlerResult;
import ru.posts.social.inbound.SocialEventHandler;
import ru.posts.social.inbound.SocialEventKind;
import ru.posts.social.inbound.SocialInboundEvent;

/**
 * Inbound {@code POST_NEW} consumer for crosspost. Enqueues deferred items (L2 +15s); heavy work runs
 * in {@link ru.posts.crosspost.process.CrosspostDeferredScheduler}.
 */
@Profile("!openapi")
@Component
public class CrosspostEventHandler implements SocialEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CrosspostEventHandler.class);

    private final CrosspostRuleRepository ruleRepository;
    private final CrosspostItemRepository itemRepository;
    private final ChannelRepository channelRepository;
    private final LoopGuard loopGuard;
    private final CrosspostProperties properties;
    private final CrosspostMetrics metrics;

    public CrosspostEventHandler(
            CrosspostRuleRepository ruleRepository,
            CrosspostItemRepository itemRepository,
            ChannelRepository channelRepository,
            RemotePostRegistryJdbc registryJdbc,
            CrosspostProperties properties,
            CrosspostMetrics metrics) {
        this.ruleRepository = ruleRepository;
        this.itemRepository = itemRepository;
        this.channelRepository = channelRepository;
        this.loopGuard = new LoopGuard(registryJdbc, properties);
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public String consumerKey() {
        return "crosspost";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean supports(SocialEventKind kind, String network) {
        if (kind != SocialEventKind.POST_NEW) {
            return false;
        }
        String n = network == null ? "" : network.toLowerCase(java.util.Locale.ROOT);
        return "vk".equals(n) || "tg".equals(n) || "max".equals(n);
    }

    @Override
    @Transactional
    public HandlerResult handle(SocialInboundEvent event, HandlerContext ctx) {
        // TG groups: only posts from the group itself (sender_chat.id == chat.id → author.community)
        if ("tg".equalsIgnoreCase(event.network())
                && event.author() != null
                && !event.author().community()) {
            // channel_post authors are typically channel (=community); plain group user posts skipped
            String chatType = extractChatType(event);
            if ("group".equals(chatType) || "supergroup".equals(chatType)) {
                metrics.itemStatus("skipped_own", "tg_group_not_from_group");
                return HandlerResult.CONTINUE;
            }
        }

        // L7 Max bot identity
        String ourBotId = resolveBotUserId(event.channelId());
        String senderId = event.author() == null ? null : event.author().id();
        if (event.author() != null && event.author().bot() && "max".equalsIgnoreCase(event.network())) {
            LoopGuard.Decision bot = loopGuard.checkBotIdentity(event.network(), senderId, ourBotId);
            if (bot.block() || (ourBotId != null && ourBotId.equals(senderId))) {
                metrics.loopBlocked(LoopGuard.Layer.L7_BOT.name());
                metrics.itemStatus("skipped_own", "max_bot_identity");
                return HandlerResult.CONTINUE;
            }
            // Even without known bot id, Max messages from bots that match channel bot are skipped via L1
            if (senderId != null && ourBotId == null && event.author().bot()) {
                // Prefer skipping Max bot-authored posts (vposter parity) when author is a bot.
                metrics.loopBlocked(LoopGuard.Layer.L7_BOT.name());
                metrics.itemStatus("skipped_own", "max_bot_author");
                return HandlerResult.CONTINUE;
            }
        }

        List<CrosspostRuleEntity> rules = ruleRepository.findBySourceChannelIdAndStatus(event.channelId(), "active");
        if (rules.isEmpty()) {
            return HandlerResult.CONTINUE;
        }

        CrosspostCandidate candidate = InboundCandidateFactory.fromEvent(event);
        String remoteId = candidate.remoteId();
        Instant processAfter = Instant.now().plus(properties.getDeferSeconds());
        String fingerprint = ContentFingerprint.fingerprint(candidate.text(), List.of());
        long simhash = SimHasher.fingerprint(candidate.text());

        for (CrosspostRuleEntity rule : rules) {
            if (itemRepository
                    .findByRuleIdAndSourceRemoteId(rule.getId(), remoteId)
                    .isPresent()) {
                continue; // idempotent
            }
            CrosspostItemEntity item = new CrosspostItemEntity();
            item.setId(UuidV7.generate());
            item.setWorkspaceId(rule.getWorkspaceId());
            item.setRuleId(rule.getId());
            item.setSourceChannelId(event.channelId());
            item.setSourceRemoteId(remoteId);
            item.setSourceNetwork(event.network());
            item.setStatus("received");
            item.setContentText(candidate.text());
            item.setAttachmentsJson(candidate.attachments());
            item.setContentFingerprint(fingerprint);
            item.setSimhash(simhash);
            item.setProcessAfter(processAfter);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("candidate", candidateFlags(candidate));
            payload.put("hop", 0);
            payload.put("dedupKey", event.dedupKey());
            payload.put(
                    "receivedAt",
                    event.receivedAt() == null
                            ? Instant.now().toString()
                            : event.receivedAt().toString());
            item.setPayload(payload);
            item.setCreatedAt(Instant.now());
            item.setUpdatedAt(Instant.now());
            itemRepository.save(item);
            metrics.itemStatus("received", "enqueued");
            log.debug("Crosspost deferred item rule={} remote={} after={}", rule.getId(), remoteId, processAfter);
        }
        return HandlerResult.CONTINUE;
    }

    private String resolveBotUserId(UUID channelId) {
        return channelRepository
                .findByIdAndDeletedAtIsNull(channelId)
                .map(ChannelEntity::getExternalId)
                .orElse(null);
    }

    private static String extractChatType(SocialInboundEvent event) {
        String raw = event.raw();
        if (raw == null) {
            return "";
        }
        // lightweight parse without failing
        if (raw.contains("\"chat\"") && raw.contains("\"type\":\"channel\"")) {
            return "channel";
        }
        if (raw.contains("\"type\":\"supergroup\"")) {
            return "supergroup";
        }
        if (raw.contains("\"type\":\"group\"")) {
            return "group";
        }
        return "";
    }

    private static Map<String, Object> candidateFlags(CrosspostCandidate c) {
        Map<String, Object> m = new HashMap<>();
        m.put("markedAsAds", c.markedAsAds());
        m.put("hasErid", c.hasErid());
        m.put("isRepost", c.isRepost());
        m.put("fromGroup", c.fromGroup());
        m.put("hasPoll", c.hasPoll());
        m.put("hasGeo", c.hasGeo());
        m.put("hasVideo", c.hasVideo());
        m.put("hasClip", c.hasClip());
        m.put("hasAudio", c.hasAudio());
        m.put("hasImage", c.hasImage());
        m.put("hasLinkAttachment", c.hasLinkAttachment());
        m.put("hasSnippet", c.hasSnippet());
        m.put("hasArticle", c.hasArticle());
        return m;
    }
}
