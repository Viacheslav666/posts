package ru.posts.crosspost.process;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.content.hash.ContentFingerprint;
import ru.posts.content.hash.SimHasher;
import ru.posts.crosspost.config.CrosspostProperties;
import ru.posts.crosspost.filter.CrosspostFilterEngine;
import ru.posts.crosspost.loop.CircuitBreaker;
import ru.posts.crosspost.loop.CrosspostLineageResolver;
import ru.posts.crosspost.loop.LoopGuard;
import ru.posts.crosspost.metrics.CrosspostMetrics;
import ru.posts.crosspost.model.CrosspostCandidate;
import ru.posts.crosspost.publish.CrosspostPostFactory;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.entity.CrosspostRuleTargetEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.jdbc.RemotePostRegistryJdbc;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;
import ru.posts.persistence.repository.CrosspostRuleTargetRepository;
import ru.posts.persistence.repository.OutboxRepository;

@Profile("!openapi")
@Service
public class CrosspostItemProcessor {

    private static final Logger log = LoggerFactory.getLogger(CrosspostItemProcessor.class);

    private static final long TG_CLOUD_MAX_BYTES = 20L * 1024 * 1024;
    private static final long MAX_ATTACHMENT_MAX_BYTES = 100L * 1024 * 1024;

    private final CrosspostItemRepository itemRepository;
    private final CrosspostRuleRepository ruleRepository;
    private final CrosspostRuleTargetRepository targetRepository;
    private final ChannelRepository channelRepository;
    private final LoopGuard loopGuard;
    private final CircuitBreaker circuitBreaker;
    private final CrosspostPostFactory postFactory;
    private final CrosspostMetrics metrics;
    private final CrosspostProperties properties;
    private final OutboxRepository outboxRepository;
    private final RemotePostRegistryJdbc registryJdbc;
    private final String localBotApiUrl;

    public CrosspostItemProcessor(
            CrosspostItemRepository itemRepository,
            CrosspostRuleRepository ruleRepository,
            CrosspostRuleTargetRepository targetRepository,
            ChannelRepository channelRepository,
            RemotePostRegistryJdbc registryJdbc,
            CrosspostProperties properties,
            CrosspostPostFactory postFactory,
            CrosspostMetrics metrics,
            OutboxRepository outboxRepository,
            @org.springframework.beans.factory.annotation.Value("${posts.telegram.local-bot-api-url:}")
                    String localBotApiUrl) {
        this.itemRepository = itemRepository;
        this.ruleRepository = ruleRepository;
        this.targetRepository = targetRepository;
        this.channelRepository = channelRepository;
        this.loopGuard = new LoopGuard(registryJdbc, properties);
        this.circuitBreaker = new CircuitBreaker(itemRepository, ruleRepository, properties);
        this.postFactory = postFactory;
        this.metrics = metrics;
        this.properties = properties;
        this.outboxRepository = outboxRepository;
        this.registryJdbc = registryJdbc;
        this.localBotApiUrl = localBotApiUrl == null ? "" : localBotApiUrl.trim();
    }

    @Transactional
    public void processDue(CrosspostItemEntity item) {
        CrosspostRuleEntity rule = ruleRepository.findById(item.getRuleId()).orElse(null);
        if (rule == null || !"active".equals(rule.getStatus())) {
            mark(item, "filtered_out", List.of("rule_inactive"));
            return;
        }

        CrosspostCandidate candidate = candidateFromItem(item);
        String fingerprint = item.getContentFingerprint();
        if (fingerprint == null || fingerprint.isBlank()) {
            fingerprint = ContentFingerprint.fingerprint(candidate.text(), List.of());
            item.setContentFingerprint(fingerprint);
            item.setSimhash(SimHasher.fingerprint(candidate.text()));
        }

        // L1
        LoopGuard.Decision own =
                loopGuard.checkOwnRegistry(item.getSourceChannelId(), item.getSourceRemoteId(), rule.getConfig());
        if (own.block()) {
            finishSkip(item, rule, own);
            return;
        }
        // L2
        LoopGuard.Decision intent = loopGuard.checkIntentRace(item.getSourceChannelId(), fingerprint);
        if (intent.block()) {
            finishSkip(item, rule, intent);
            return;
        }

        List<String> filterReasons = CrosspostFilterEngine.evaluate(candidate, rule.getConfig());
        if (!filterReasons.isEmpty()) {
            mark(item, "filtered_out", filterReasons);
            metrics.itemStatus("filtered_out", filterReasons.getFirst());
            return;
        }

        String mediaSkip = unsupportedMediaReason(candidate);
        if (mediaSkip != null) {
            mark(item, "skipped_unsupported_media", List.of(mediaSkip));
            metrics.itemStatus("skipped_unsupported_media", mediaSkip);
            return;
        }

        List<CrosspostRuleTargetEntity> targets = targetRepository.findByRuleIdOrderByCreatedAtAsc(rule.getId());
        Map<UUID, ChannelEntity> channels = new HashMap<>();
        channelRepository
                .findByIdAndDeletedAtIsNull(item.getSourceChannelId())
                .ifPresent(c -> channels.put(c.getId(), c));
        for (CrosspostRuleTargetEntity t : targets) {
            channelRepository
                    .findByIdAndDeletedAtIsNull(t.getTargetChannelId())
                    .ifPresent(c -> channels.put(c.getId(), c));
        }

        Map<UUID, LoopGuard.Decision> decisions = new HashMap<>();
        CrosspostLineageResolver.Resolved lineageState = CrosspostLineageResolver.resolve(item, registryJdbc);
        Map<String, Object> lineageRef = lineageState.sourceRef();
        short lineageHop = lineageState.hop();
        long sim = item.getSimhash() == null ? SimHasher.fingerprint(candidate.text()) : item.getSimhash();
        boolean anyLoop = false;
        for (CrosspostRuleTargetEntity t : targets) {
            LoopGuard.Decision lineage =
                    loopGuard.checkLineage(t.getTargetChannelId(), item.getSourceChannelId(), lineageRef, lineageHop);
            if (lineage.block()) {
                decisions.put(t.getTargetChannelId(), lineage);
                metrics.loopBlocked(lineage.layer().name());
                anyLoop = true;
                continue;
            }
            LoopGuard.Decision dedup = loopGuard.checkTargetDedup(t.getTargetChannelId(), fingerprint, sim);
            if (dedup.block()) {
                decisions.put(t.getTargetChannelId(), dedup);
                metrics.loopBlocked(dedup.layer().name());
            }
        }

        CrosspostPostFactory.PublishResult published =
                postFactory.publish(rule, item, candidate, targets, channels, decisions, Instant.now(), false);

        List<String> reasons = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        for (CrosspostPostFactory.TargetPublish tp : published.targets()) {
            if ("queued".equals(tp.status())) {
                queued++;
            } else if (tp.status() != null && tp.status().startsWith("skipped")) {
                skipped++;
                if (tp.reason() != null) {
                    reasons.add(tp.reason());
                }
            }
        }

        CrosspostItemEntity previous = itemRepository
                .findFirstByRuleIdOrderByCreatedAtDesc(rule.getId())
                .filter(i -> !i.getId().equals(item.getId()))
                .orElse(null);

        if (queued == 0) {
            String status = anyLoop
                            || decisions.values().stream().anyMatch(d -> d.block() && "skipped_loop".equals(d.status()))
                    ? "skipped_loop"
                    : decisions.values().stream().anyMatch(d -> d.block() && "skipped_duplicate".equals(d.status()))
                            ? "skipped_duplicate"
                            : "filtered_out";
            item.setConsecutiveLoopSkips(CircuitBreaker.nextConsecutiveLoopSkips(previous, status));
            mark(item, status, reasons.isEmpty() ? List.of(status) : reasons);
            metrics.itemStatus(status, reasons.isEmpty() ? status : reasons.getFirst());
            circuitBreaker.evaluateAndMaybePause(rule, item);
            maybeNotifyAutoPause(rule);
            return;
        }

        item.setConsecutiveLoopSkips(0);
        Map<String, Object> outbound = CrosspostLineageResolver.outboundLineage(item, rule.getId(), item.getPayload());
        item.setPayload(CrosspostLineageResolver.mergePayload(item.getPayload(), outbound));
        mark(item, "queued", reasons);
        metrics.itemStatus("queued", skipped > 0 ? "partial_targets" : "ok");
        metrics.recordLag(candidate.publishedAt());
        circuitBreaker.evaluateAndMaybePause(rule, item);
        maybeNotifyAutoPause(rule);
        log.debug(
                "Crosspost item {} queued post={} pubs={}",
                item.getId(),
                published.postId(),
                published.targets().size());
    }

    private void maybeNotifyAutoPause(CrosspostRuleEntity rule) {
        if (!"auto_paused".equals(rule.getStatus())) {
            return;
        }
        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("crosspost_rule");
        outbox.setAggregateId(rule.getId());
        outbox.setEventType("crosspost.rule_auto_paused");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", rule.getWorkspaceId().toString());
        payload.put("ruleId", rule.getId().toString());
        payload.put("reason", rule.getConfig() == null ? null : rule.getConfig().get("auto_pause_reason"));
        outbox.setPayload(payload);
        outbox.setCreatedAt(Instant.now());
        outboxRepository.save(outbox);
    }

    private void finishSkip(CrosspostItemEntity item, CrosspostRuleEntity rule, LoopGuard.Decision decision) {
        CrosspostItemEntity previous = itemRepository
                .findFirstByRuleIdOrderByCreatedAtDesc(rule.getId())
                .filter(i -> !i.getId().equals(item.getId()))
                .orElse(null);
        item.setConsecutiveLoopSkips(CircuitBreaker.nextConsecutiveLoopSkips(previous, decision.status()));
        mark(item, decision.status(), List.of(decision.reason()));
        metrics.itemStatus(decision.status(), decision.reason());
        if (decision.layer() != null) {
            metrics.loopBlocked(decision.layer().name());
        }
        circuitBreaker.evaluateAndMaybePause(rule, item);
        maybeNotifyAutoPause(rule);
    }

    private void mark(CrosspostItemEntity item, String status, List<String> reasons) {
        item.setStatus(status);
        item.setReasons(reasons.toArray(String[]::new));
        item.setUpdatedAt(Instant.now());
        itemRepository.save(item);
    }

    private String unsupportedMediaReason(CrosspostCandidate candidate) {
        if (candidate.attachments() == null) {
            return null;
        }
        boolean localBot = !localBotApiUrl.isBlank();
        for (Map<String, Object> att : candidate.attachments()) {
            Object sizeObj = att.get("sizeBytes");
            long size = sizeObj instanceof Number n ? n.longValue() : -1L;
            String type = String.valueOf(att.getOrDefault("type", ""));
            if ("tg".equals(candidate.network()) && size > TG_CLOUD_MAX_BYTES && !localBot) {
                return "tg_file_over_20mb";
            }
            if ("max".equals(candidate.network()) && size > MAX_ATTACHMENT_MAX_BYTES) {
                return "max_attachment_over_100mb";
            }
            if ("tg".equals(candidate.network()) && "video".equals(type) && size > 40L * 1024 * 1024 && !localBot) {
                return "tg_video_over_limit";
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private CrosspostCandidate candidateFromItem(CrosspostItemEntity item) {
        Map<String, Object> payload = item.getPayload() == null ? Map.of() : item.getPayload();
        Object stored = payload.get("candidate");
        if (stored instanceof Map<?, ?> map) {
            // Rebuild lightly from stored attachments/text
            return CrosspostCandidate.builder()
                    .network(item.getSourceNetwork())
                    .remoteId(item.getSourceRemoteId())
                    .text(item.getContentText())
                    .attachments(item.getAttachmentsJson())
                    .markedAsAds(bool(map.get("markedAsAds")))
                    .hasErid(bool(map.get("hasErid")))
                    .isRepost(bool(map.get("isRepost")))
                    .fromGroup(bool(map.get("fromGroup")))
                    .hasPoll(bool(map.get("hasPoll")))
                    .hasGeo(bool(map.get("hasGeo")))
                    .hasVideo(bool(map.get("hasVideo")))
                    .hasClip(bool(map.get("hasClip")))
                    .hasAudio(bool(map.get("hasAudio")))
                    .hasImage(bool(map.get("hasImage")))
                    .hasLinkAttachment(bool(map.get("hasLinkAttachment")))
                    .hasSnippet(bool(map.get("hasSnippet")))
                    .hasArticle(bool(map.get("hasArticle")))
                    .publishedAt(item.getCreatedAt())
                    .build();
        }
        return CrosspostCandidate.builder()
                .network(item.getSourceNetwork())
                .remoteId(item.getSourceRemoteId())
                .text(item.getContentText())
                .attachments(item.getAttachmentsJson())
                .publishedAt(item.getCreatedAt())
                .build();
    }

    private static boolean bool(Object raw) {
        return raw instanceof Boolean b && b;
    }

    public LoopGuard loopGuard() {
        return loopGuard;
    }

    public CrosspostProperties properties() {
        return properties;
    }

    /** Preview path used by API — no persistence of publications. */
    @Transactional(readOnly = true)
    public CrosspostPostFactory.PublishResult preview(
            CrosspostRuleEntity rule,
            CrosspostCandidate candidate,
            List<CrosspostRuleTargetEntity> targets,
            Map<UUID, ChannelEntity> channels) {
        Map<UUID, LoopGuard.Decision> decisions = new HashMap<>();
        String fingerprint = ContentFingerprint.fingerprint(candidate.text(), List.of());
        long sim = SimHasher.fingerprint(candidate.text());
        Map<String, Object> lineageRef = Map.of(
                "root_origin",
                Map.of(
                        "network", candidate.network(),
                        "channel_id", rule.getSourceChannelId().toString(),
                        "remote_id", candidate.remoteId()),
                "hop",
                0,
                "visited_channel_ids",
                List.of(rule.getSourceChannelId().toString()));
        for (CrosspostRuleTargetEntity t : targets) {
            LoopGuard.Decision lineage =
                    loopGuard.checkLineage(t.getTargetChannelId(), rule.getSourceChannelId(), lineageRef, (short) 0);
            if (lineage.block()) {
                decisions.put(t.getTargetChannelId(), lineage);
                continue;
            }
            LoopGuard.Decision dedup = loopGuard.checkTargetDedup(t.getTargetChannelId(), fingerprint, sim);
            if (dedup.block()) {
                decisions.put(t.getTargetChannelId(), dedup);
            }
        }
        CrosspostItemEntity fake = new CrosspostItemEntity();
        fake.setId(UUID.randomUUID());
        fake.setSourceChannelId(rule.getSourceChannelId());
        fake.setSourceRemoteId(candidate.remoteId());
        fake.setSourceNetwork(candidate.network());
        return postFactory.publish(rule, fake, candidate, targets, channels, decisions, Instant.now(), true);
    }
}
