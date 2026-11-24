package ru.posts.grabber;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.posts.common.uuid.UuidV7;
import ru.posts.grabber.config.GrabberConfigSupport;
import ru.posts.grabber.config.SourceIdNormalizer;
import ru.posts.grabber.fetch.GrabberSourceScan;
import ru.posts.grabber.fetch.RssFetcher;
import ru.posts.grabber.fetch.SourceCandidate;
import ru.posts.grabber.fetch.VkWallFetcher;
import ru.posts.grabber.fetch.WebsiteFetcher;
import ru.posts.grabber.filter.GrabberFilters;
import ru.posts.grabber.hash.ContentHasher;
import ru.posts.grabber.hash.SimHasher;
import ru.posts.grabber.notify.GrabberSettingReporter;
import ru.posts.grabber.publish.GrabberPostFactory;
import ru.posts.grabber.target.GrabberDailyWindow;
import ru.posts.grabber.target.GrabberPublishSchedule;
import ru.posts.grabber.token.GrabberTokenProvider;
import ru.posts.grabber.transform.ContentTransformer;
import ru.posts.grabber.transform.MacroExpander;
import ru.posts.persistence.entity.AutomationRuleEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.entity.GrabberItemEntity;
import ru.posts.persistence.entity.GrabberRunEntity;
import ru.posts.persistence.entity.OutboxEntity;
import ru.posts.persistence.repository.AutomationRuleRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.persistence.repository.GrabberItemRepository;
import ru.posts.persistence.repository.GrabberRunRepository;
import ru.posts.persistence.repository.OutboxRepository;

@Service
@Profile("!openapi")
public class GrabberRunProcessor {

    private static final Logger log = LoggerFactory.getLogger(GrabberRunProcessor.class);
    private static final List<String> DEDUP_EXCLUDED = List.of("rejected", "suppressed_dedup", "filtered_out");

    private final AutomationRuleRepository ruleRepository;
    private final GrabberRunRepository runRepository;
    private final GrabberItemRepository itemRepository;
    private final ChannelRepository channelRepository;
    private final RssFetcher rssFetcher;
    private final WebsiteFetcher websiteFetcher;
    private final VkWallFetcher vkWallFetcher;
    private final GrabberTokenProvider tokenProvider;
    private final GrabberPostFactory postFactory;
    private final MeterRegistry meterRegistry;
    private final OutboxRepository outboxRepository;
    private final TransactionTemplate tx;
    private final GrabberSettingReporter settingReporter;

    public GrabberRunProcessor(
            AutomationRuleRepository ruleRepository,
            GrabberRunRepository runRepository,
            GrabberItemRepository itemRepository,
            ChannelRepository channelRepository,
            RssFetcher rssFetcher,
            WebsiteFetcher websiteFetcher,
            VkWallFetcher vkWallFetcher,
            GrabberTokenProvider tokenProvider,
            GrabberPostFactory postFactory,
            MeterRegistry meterRegistry,
            OutboxRepository outboxRepository,
            PlatformTransactionManager transactionManager,
            GrabberSettingReporter settingReporter) {
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.channelRepository = channelRepository;
        this.rssFetcher = rssFetcher;
        this.websiteFetcher = websiteFetcher;
        this.vkWallFetcher = vkWallFetcher;
        this.tokenProvider = tokenProvider;
        this.postFactory = postFactory;
        this.meterRegistry = meterRegistry;
        this.outboxRepository = outboxRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.settingReporter = settingReporter;
    }

    /**
     * Not transactional: candidate fetch and {@code publishItem} perform network I/O. Database
     * writes use short {@link TransactionTemplate} transactions (and Spring Data repository
     * transactions) so a pooled connection is not held across downloads or VK uploads.
     */
    public GrabberRunEntity process(UUID ruleId, UUID runId, String trigger, String traceId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AutomationRuleEntity rule = ruleRepository
                .findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + ruleId));
        // migrate config on read
        rule.setConfig(GrabberConfigSupport.withDefaults(rule.getConfig()));
        GrabberRunEntity run = runId == null
                ? createRun(rule, trigger, traceId)
                : runRepository.findById(runId).orElseGet(() -> createRun(rule, trigger, traceId));
        if (!"running".equals(run.getStatus()) && run.getFinishedAt() != null) {
            return run; // idempotent redelivery of finished run
        }
        Instant now = Instant.now();
        try {
            ChannelEntity channel = channelRepository
                    .findByIdAndDeletedAtIsNull(rule.getTargetChannelId())
                    .orElse(null);
            if (channel == null) {
                failRun(rule, run, "ingest_source_unreachable", "Target channel missing", now);
                return run;
            }
            if ("reauth_required".equals(channel.getStatus())) {
                rule.setStatus("auto_paused");
                rule.setUpdatedAt(now);
                saveRule(rule);
                failRun(rule, run, "channel_reauth_required", "Target channel requires reauth", now);
                return run;
            }

            List<SourceCandidate> candidates = fetchCandidates(rule);
            run.setSourcesScanned(Math.max(1, countSources(rule)));
            run.setItemsFound(candidates.size());

            int itemsNew = 0;
            int itemsDeduped = 0;
            int itemsPublished = 0;
            String publishMode = GrabberConfigSupport.publishMode(rule.getConfig());
            List<GrabberItemEntity> recentSim = loadRecentSimhash(rule);
            Map<String, Integer> settingSkips = new LinkedHashMap<>();
            Instant anchor = GrabberPublishSchedule.spacedBase(rule.getConfig(), now, lastAcceptedAt(rule));
            int slot = 0;

            for (SourceCandidate candidate : candidates) {
                String skip = GrabberFilters.skipReason(candidate, rule.getConfig(), now);
                String contentHash = ContentHasher.hash(
                        candidate.contentText(), candidate.mediaHashes(), candidate.attachmentCanonicalIds());
                long simhash = SimHasher.fingerprint(candidate.contentText());

                var existing = itemRepository.findByRuleIdAndSourceNetworkAndSourceIdAndRemotePostId(
                        rule.getId(), candidate.sourceNetwork(), candidate.sourceId(), candidate.remotePostId());
                if (existing.isPresent()) {
                    GrabberItemEntity prior = existing.get();
                    if (GrabberDailyWindow.isReopenableRateLimit(prior.getStatus(), prior.getSkipReason())) {
                        itemRepository.delete(prior);
                        itemRepository.flush();
                    } else {
                        itemsDeduped++;
                        continue;
                    }
                }

                if (GrabberConfigSupport.dedupEnabled(rule.getConfig())
                        && itemRepository.existsByWorkspaceIdAndTargetChannelIdAndContentHashAndStatusNotIn(
                                rule.getWorkspaceId(), rule.getTargetChannelId(), contentHash, DEDUP_EXCLUDED)) {
                    persistItem(rule, run, candidate, contentHash, simhash, "suppressed_dedup", "content_hash", now);
                    itemsDeduped++;
                    meterRegistry.counter("grabber_deduped").increment();
                    continue;
                }

                if (GrabberConfigSupport.similarityDedupEnabled(rule.getConfig())
                        && isNearDuplicate(
                                simhash, recentSim, GrabberConfigSupport.similarityThreshold(rule.getConfig()))) {
                    persistItem(rule, run, candidate, contentHash, simhash, "suppressed_dedup", "similarity", now);
                    itemsDeduped++;
                    meterRegistry.counter("grabber_deduped_similarity").increment();
                    continue;
                }

                if (skip != null) {
                    noteSettingSkip(settingSkips, skip);
                    persistItem(rule, run, candidate, contentHash, simhash, "filtered_out", skip, now);
                    continue;
                }

                String limitReason = quotaSkipReasonUnderRuleLock(rule, candidate, now);
                if (limitReason != null) {
                    noteSettingSkip(settingSkips, limitReason);
                    log.info(
                            "Grabber limit ruleId={} setting={} sourceId={} remotePostId={}",
                            rule.getId(),
                            limitReason,
                            candidate.sourceId(),
                            candidate.remotePostId());
                    // No filtered_out row: a terminal skip would dedupe the post forever, past the reset.
                    continue;
                }

                MacroExpander.Context macroCtx = MacroExpander.Context.of(
                        candidate.sourceName(),
                        candidate.sourceId(),
                        candidate.remotePostId(),
                        candidate.contentText());
                // Attachment stripping is stored for moderation. The text stays the raw source so
                // publishItem is the only place that applies prepend, append and word replacement.
                ContentTransformer.TransformResult transformed = ContentTransformer.transform(
                        candidate.contentText(), candidate.attachments(), rule.getConfig(), macroCtx);
                applyAuthorSignature(rule, candidate, candidate.contentText());

                GrabberItemEntity item =
                        persistItem(rule, run, candidate, contentHash, simhash, initialStatus(publishMode), null, now);
                item.setAttachmentsJson(transformed.attachments());
                itemRepository.save(item);
                recentSim.add(0, item);
                if (recentSim.size() > GrabberConfigSupport.similarityLookback(rule.getConfig())) {
                    recentSim = new ArrayList<>(
                            recentSim.subList(0, GrabberConfigSupport.similarityLookback(rule.getConfig())));
                }

                if (!GrabberConfigSupport.PUBLISH_MODE_AUTO.equals(publishMode)) {
                    item.setStatus("pending_moderation");
                    itemRepository.save(item);
                    itemsNew++;
                    meterRegistry.counter("grabber_items_new").increment();
                    continue;
                }

                // auto
                if (rateLimited(rule, now)) {
                    item.setStatus("pending_moderation");
                    item.setSkipReason("rate_limit");
                    itemRepository.save(item);
                    itemsNew++;
                    continue;
                }
                try {
                    GrabberPostFactory.PublishResult published =
                            postFactory.publishItem(rule, item, rule.getCreatedBy(), null, anchor, slot);
                    if (published.postId() != null) {
                        slot++;
                    } else if (item.getSkipReason() != null) {
                        noteSettingSkip(settingSkips, item.getSkipReason());
                    }
                    itemRepository.save(item);
                    itemsNew++;
                    if (published.postId() != null) {
                        itemsPublished++;
                    }
                    meterRegistry.counter("grabber_items_new").increment();
                } catch (GrabberPostFactory.ChannelReauthRequiredException ex) {
                    rule.setStatus("auto_paused");
                    rule.setUpdatedAt(now);
                    saveRule(rule);
                    item.setStatus("failed_ingest");
                    item.setSkipReason("channel_reauth_required");
                    itemRepository.save(item);
                    throw ex;
                } catch (DataIntegrityViolationException ex) {
                    itemsDeduped++;
                }
            }

            reportSettingSkips(rule, settingSkips);

            int newCount = itemsNew;
            int dedupedCount = itemsDeduped;
            int publishedCount = itemsPublished;
            tx.executeWithoutResult(status -> {
                run.setItemsNew(newCount);
                run.setItemsDeduped(dedupedCount);
                run.setItemsPublished(publishedCount);
                run.setStatus("succeeded");
                run.setFinishedAt(now);
                runRepository.save(run);

                if (newCount > 0 && GrabberConfigSupport.PUBLISH_MODE_MODERATE.equals(publishMode)) {
                    OutboxEntity outbox = new OutboxEntity();
                    outbox.setAggregate("grabber_run");
                    outbox.setAggregateId(run.getId());
                    outbox.setEventType("grabber.moderation_queued");
                    outbox.setPayload(Map.of(
                            "workspaceId", rule.getWorkspaceId().toString(),
                            "ruleId", rule.getId().toString(),
                            "runId", run.getId().toString(),
                            "itemsNew", newCount,
                            "publishMode", publishMode));
                    outbox.setCreatedAt(now);
                    outboxRepository.save(outbox);
                }

                rule.setLastRunAt(now);
                rule.setConsecutiveFailures(0);
                int interval = GrabberConfigSupport.scanIntervalMinutes(rule.getConfig());
                rule.setNextRunAt(now.plus(interval, ChronoUnit.MINUTES));
                rule.setUpdatedAt(now);
                saveRule(rule);
            });
            return run;
        } catch (Exception ex) {
            log.warn("Grabber run failed ruleId={} runId={}: {}", ruleId, run.getId(), ex.getMessage());
            failRun(rule, run, "ingest_source_unreachable", ex.getMessage(), Instant.now());
            maybeAutoPause(rule);
            return run;
        } finally {
            sample.stop(meterRegistry.timer("grabber_run_duration"));
        }
    }

    private List<GrabberItemEntity> loadRecentSimhash(AutomationRuleEntity rule) {
        int lookback = GrabberConfigSupport.similarityLookback(rule.getConfig());
        PageRequest page = PageRequest.of(0, lookback);
        Map<String, Object> cfg = GrabberConfigSupport.withDefaults(rule.getConfig());
        Object rel = cfg.get("reliability");
        String dedupScope = "channel";
        if (rel instanceof Map<?, ?> map && map.get("dedup_scope") != null) {
            dedupScope = map.get("dedup_scope").toString();
        }
        if ("rule".equalsIgnoreCase(dedupScope)) {
            return new ArrayList<>(itemRepository.findRecentWithSimhashByRule(rule.getId(), DEDUP_EXCLUDED, page));
        }
        return new ArrayList<>(itemRepository.findRecentWithSimhashByChannel(
                rule.getWorkspaceId(), rule.getTargetChannelId(), DEDUP_EXCLUDED, page));
    }

    private static boolean isNearDuplicate(long simhash, List<GrabberItemEntity> recent, double threshold) {
        for (GrabberItemEntity item : recent) {
            if (item.getSimhash() == null) {
                continue;
            }
            if (SimHasher.similarEnough(simhash, item.getSimhash(), threshold)) {
                return true;
            }
        }
        return false;
    }

    private String quotaSkipReasonUnderRuleLock(AutomationRuleEntity rule, SourceCandidate candidate, Instant now) {
        return tx.execute(status -> {
            AutomationRuleEntity locked = ruleRepository
                    .findByIdForUpdate(rule.getId())
                    .orElseThrow(() -> new IllegalStateException("Rule not found: " + rule.getId()));
            return quotaSkipReason(locked, candidate, now);
        });
    }

    private String quotaSkipReason(AutomationRuleEntity rule, SourceCandidate candidate, Instant now) {
        Instant dayStart = GrabberDailyWindow.start(rule.getConfig(), now);
        int perDay = GrabberConfigSupport.rateLimitPerDay(rule.getConfig());
        if (perDay > 0) {
            long count = itemRepository.countByRuleIdAndStatusInAndDetectedAtAfter(
                    rule.getId(), GrabberDailyWindow.QUOTA_STATUSES, dayStart);
            if (count >= perDay) {
                return GrabberDailyWindow.RATE_LIMIT_PER_DAY;
            }
        }
        int perSource = GrabberConfigSupport.rateLimitPerSourcePerDay(rule.getConfig());
        if (perSource > 0) {
            long count = itemRepository.countByRuleIdAndSourceIdAndStatusInAndDetectedAtAfter(
                    rule.getId(), candidate.sourceId(), GrabberDailyWindow.QUOTA_STATUSES, dayStart);
            if (count >= perSource) {
                return GrabberDailyWindow.RATE_LIMIT_PER_SOURCE;
            }
        }
        return null;
    }

    private Instant lastAcceptedAt(AutomationRuleEntity rule) {
        return itemRepository
                .findFirstByRuleIdAndStatusInOrderByDetectedAtDesc(
                        rule.getId(), List.of("scheduled", "published", "approved", "pending_moderation"))
                .map(GrabberItemEntity::getDetectedAt)
                .orElse(null);
    }

    private static void noteSettingSkip(Map<String, Integer> skips, String reason) {
        if (GrabberSettingReporter.settingForReason(reason) == null) {
            return;
        }
        skips.merge(reason, 1, Integer::sum);
    }

    private void reportSettingSkips(AutomationRuleEntity rule, Map<String, Integer> skips) {
        for (Map.Entry<String, Integer> entry : skips.entrySet()) {
            String setting = GrabberSettingReporter.settingForReason(entry.getKey());
            settingReporter.report(
                    rule,
                    setting,
                    entry.getKey(),
                    GrabberSettingReporter.detailForReason(entry.getKey(), entry.getValue()));
        }
    }

    private List<SourceCandidate> fetchCandidates(AutomationRuleEntity rule) {
        if ("rss".equals(rule.getType())) {
            String url = GrabberConfigSupport.rssUrl(rule.getConfig());
            int max = rule.getConfig().get("rss_max_items_per_run") instanceof Number n ? n.intValue() : 20;
            return rssFetcher.fetch(url, max);
        }
        if ("website".equals(rule.getType())) {
            return websiteFetcher.fetch(
                    GrabberConfigSupport.websiteUrls(rule.getConfig()),
                    GrabberConfigSupport.websiteSelector(rule.getConfig()));
        }
        List<String> sourceIds = SourceIdNormalizer.normalizeAll(GrabberConfigSupport.sourceIds(rule.getConfig()));
        Optional<String> token = tokenProvider.resolveVkToken(rule.getWorkspaceId(), rule.getTargetChannelId());
        if (token.isEmpty()) {
            throw new IllegalStateException("No VK token available for grabber sources");
        }
        String accessToken = token.get();
        List<String> failed = new ArrayList<>();
        List<SourceCandidate> found = GrabberSourceScan.collect(
                sourceIds, sourceId -> vkWallFetcher.fetch(sourceId, accessToken, 20), (sourceId, ex) -> {
                    failed.add(sourceId);
                    log.warn(
                            "Grabber source skipped ruleId={} sourceId={} detail={}",
                            rule.getId(),
                            sourceId,
                            GrabberSourceScan.redact(ex.getMessage(), accessToken));
                });
        if (!sourceIds.isEmpty() && failed.size() == sourceIds.size()) {
            throw new IllegalStateException("VK wall.get failed for every source");
        }
        return found;
    }

    private GrabberItemEntity persistItem(
            AutomationRuleEntity rule,
            GrabberRunEntity run,
            SourceCandidate candidate,
            String contentHash,
            long simhash,
            String status,
            String skipReason,
            Instant now) {
        GrabberItemEntity item = new GrabberItemEntity();
        item.setId(UuidV7.generate());
        item.setRuleId(rule.getId());
        item.setRunId(run.getId());
        item.setWorkspaceId(rule.getWorkspaceId());
        item.setTargetChannelId(rule.getTargetChannelId());
        item.setSourceNetwork(candidate.sourceNetwork());
        item.setSourceId(candidate.sourceId());
        item.setRemotePostId(candidate.remotePostId());
        item.setSourcePermalink(candidate.sourcePermalink());
        item.setSourceName(candidate.sourceName());
        item.setContentText(candidate.contentText());
        item.setContentHtml(candidate.contentHtml());
        item.setAttachmentsJson(candidate.attachments() == null ? List.of() : candidate.attachments());
        item.setContentHash(contentHash);
        item.setSimhash(simhash);
        item.setMediaHashes(
                candidate.mediaHashes() == null
                        ? new String[0]
                        : candidate.mediaHashes().toArray(String[]::new));
        item.setStatus(status);
        item.setSkipReason(skipReason);
        item.setRawPayload(truncateRaw(withSourceMarkers(candidate)));
        item.setDetectedAt(now);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        try {
            return itemRepository.save(item);
        } catch (DataIntegrityViolationException ex) {
            // concurrent insert — treat as dedup
            return itemRepository
                    .findByRuleIdAndSourceNetworkAndSourceIdAndRemotePostId(
                            rule.getId(), candidate.sourceNetwork(), candidate.sourceId(), candidate.remotePostId())
                    .orElseThrow(() -> ex);
        }
    }

    private String applyAuthorSignature(AutomationRuleEntity rule, SourceCandidate candidate, String text) {
        if (!GrabberConfigSupport.appendAuthorSignature(rule.getConfig())) {
            return text == null ? "" : text;
        }
        String name = signerName(candidate);
        if (candidate.signed() && name.isBlank()) {
            settingReporter.reportUserNotice(
                    rule.getCreatedBy(),
                    rule,
                    "author_signature",
                    "signer_missing",
                    "У поста есть подпись автора, но имя не пришло из VK. Подпись не добавлена.");
            return text == null ? "" : text;
        }
        return ContentTransformer.appendAuthorSignature(text, name);
    }

    private static String signerName(SourceCandidate candidate) {
        Map<String, Object> raw = candidate.rawPayload();
        if (raw == null || raw.get("signer_name") == null) {
            return "";
        }
        return raw.get("signer_name").toString().trim();
    }

    private static Map<String, Object> withSourceMarkers(SourceCandidate candidate) {
        Map<String, Object> raw = new LinkedHashMap<>();
        if (candidate.rawPayload() != null) {
            raw.putAll(candidate.rawPayload());
        }
        if (candidate.contentText() != null) {
            raw.putIfAbsent("source_text", candidate.contentText());
        }
        raw.put("signed", candidate.signed());
        return raw;
    }

    private Map<String, Object> truncateRaw(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        if (raw.size() <= 32) {
            return raw;
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        for (String key : List.of("source_text", "signer_name", "signed")) {
            if (raw.containsKey(key)) {
                kept.put(key, raw.get(key));
            }
        }
        kept.put("truncated", true);
        kept.put("keys", raw.keySet().stream().limit(16).toList());
        return kept;
    }

    private GrabberRunEntity createRun(AutomationRuleEntity rule, String trigger, String traceId) {
        GrabberRunEntity run = new GrabberRunEntity();
        run.setId(UuidV7.generate());
        run.setRuleId(rule.getId());
        run.setWorkspaceId(rule.getWorkspaceId());
        run.setTrigger(trigger == null ? "scheduled" : trigger);
        run.setStatus("running");
        run.setStartedAt(Instant.now());
        run.setTraceId(traceId);
        return runRepository.save(run);
    }

    private void failRun(AutomationRuleEntity rule, GrabberRunEntity run, String code, String message, Instant now) {
        tx.executeWithoutResult(status -> applyFailure(rule, run, code, message, now));
    }

    private void applyFailure(
            AutomationRuleEntity rule, GrabberRunEntity run, String code, String message, Instant now) {
        run.setStatus("failed");
        run.setErrorCode(code);
        run.setErrorMessage(message == null ? null : message.substring(0, Math.min(message.length(), 1000)));
        run.setFinishedAt(now);
        runRepository.save(run);
        rule.setLastRunAt(now);
        rule.setConsecutiveFailures(rule.getConsecutiveFailures() + 1);
        int interval = 180;
        try {
            interval = GrabberConfigSupport.scanIntervalMinutes(rule.getConfig());
        } catch (Exception ignored) {
            // keep default
        }
        rule.setNextRunAt(now.plus(interval, ChronoUnit.MINUTES));
        rule.setUpdatedAt(now);
        saveRule(rule);

        OutboxEntity outbox = new OutboxEntity();
        outbox.setAggregate("grabber_run");
        outbox.setAggregateId(run.getId());
        outbox.setEventType("grabber.error");
        outbox.setPayload(Map.of(
                "workspaceId", rule.getWorkspaceId().toString(),
                "ruleId", rule.getId().toString(),
                "runId", run.getId().toString(),
                "errorCode", code == null ? "" : code,
                "errorMessage", message == null ? "" : message.substring(0, Math.min(message.length(), 500))));
        outbox.setCreatedAt(now);
        outboxRepository.save(outbox);
    }

    private void maybeAutoPause(AutomationRuleEntity rule) {
        if (!GrabberConfigSupport.autoPauseOnErrors(rule.getConfig())) {
            return;
        }
        int threshold = GrabberConfigSupport.autoPauseThreshold(rule.getConfig());
        if (rule.getConsecutiveFailures() >= threshold) {
            rule.setStatus("auto_paused");
            rule.setUpdatedAt(Instant.now());
            saveRule(rule);
        }
    }

    /**
     * {@code save} merges into a new managed instance and only then bumps {@code @Version}. Copy the
     * version back so a later save of this same object does not fail with an optimistic lock.
     */
    private void saveRule(AutomationRuleEntity rule) {
        AutomationRuleEntity saved = ruleRepository.saveAndFlush(rule);
        rule.setVersion(saved.getVersion());
    }

    private boolean rateLimited(AutomationRuleEntity rule, Instant now) {
        int perHour = GrabberConfigSupport.rateLimitPerHour(rule.getConfig());
        long publishedLastHour = itemRepository.countByRuleIdAndStatusAndDetectedAtAfter(
                rule.getId(), "scheduled", now.minus(1, ChronoUnit.HOURS));
        publishedLastHour += itemRepository.countByRuleIdAndStatusAndDetectedAtAfter(
                rule.getId(), "published", now.minus(1, ChronoUnit.HOURS));
        return publishedLastHour >= perHour;
    }

    private static String initialStatus(String publishMode) {
        if (GrabberConfigSupport.PUBLISH_MODE_AUTO.equals(publishMode)) {
            return "approved";
        }
        return "pending_moderation";
    }

    private static int countSources(AutomationRuleEntity rule) {
        if ("rss".equals(rule.getType())) {
            return 1;
        }
        if ("website".equals(rule.getType())) {
            return Math.max(
                    1, GrabberConfigSupport.websiteUrls(rule.getConfig()).size());
        }
        return Math.max(
                1,
                SourceIdNormalizer.normalizeAll(GrabberConfigSupport.sourceIds(rule.getConfig()))
                        .size());
    }
}
