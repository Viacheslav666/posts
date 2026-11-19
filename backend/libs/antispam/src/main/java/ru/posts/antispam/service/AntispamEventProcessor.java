package ru.posts.antispam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import ru.posts.antispam.engine.AntispamEngine;
import ru.posts.antispam.engine.CompiledRuleCache;
import ru.posts.antispam.metrics.AntispamMetrics;
import ru.posts.antispam.model.AntispamEventView;
import ru.posts.antispam.model.AntispamSection;
import ru.posts.antispam.model.CompiledRule;
import ru.posts.antispam.model.EvaluationContext;
import ru.posts.antispam.model.Verdict;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.AntispamWordListEntity;
import ru.posts.persistence.entity.ChannelEntity;
import ru.posts.persistence.repository.AntispamRuleRepository;
import ru.posts.persistence.repository.AntispamWordListRepository;
import ru.posts.persistence.repository.ChannelRepository;
import ru.posts.social.inbound.HandlerResult;
import ru.posts.social.inbound.SocialInboundAttachment;
import ru.posts.social.inbound.SocialInboundEvent;

/** Loads rules, builds context, evaluates, applies actions. */
public class AntispamEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(AntispamEventProcessor.class);

    private final AntispamRuleRepository ruleRepository;
    private final AntispamWordListRepository wordListRepository;
    private final ChannelRepository channelRepository;
    private final CompiledRuleCache ruleCache;
    private final SectionResolver sectionResolver;
    private final MembershipCache membershipCache;
    private final RepeatDetector repeatDetector;
    private final AntispamActionApplier actionApplier;
    private final AntispamMetrics metrics;
    private final ObjectMapper objectMapper;
    private final PinnedPostCache pinnedPostCache;
    private final AdsContextResolver adsContextResolver;

    public AntispamEventProcessor(
            AntispamRuleRepository ruleRepository,
            AntispamWordListRepository wordListRepository,
            ChannelRepository channelRepository,
            CompiledRuleCache ruleCache,
            SectionResolver sectionResolver,
            MembershipCache membershipCache,
            RepeatDetector repeatDetector,
            AntispamActionApplier actionApplier,
            AntispamMetrics metrics,
            ObjectMapper objectMapper,
            PinnedPostCache pinnedPostCache,
            AdsContextResolver adsContextResolver) {
        this.ruleRepository = ruleRepository;
        this.wordListRepository = wordListRepository;
        this.channelRepository = channelRepository;
        this.ruleCache = ruleCache;
        this.sectionResolver = sectionResolver;
        this.membershipCache = membershipCache;
        this.repeatDetector = repeatDetector;
        this.actionApplier = actionApplier;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.pinnedPostCache = pinnedPostCache;
        this.adsContextResolver = adsContextResolver;
    }

    @Transactional
    public HandlerResult process(SocialInboundEvent event) {
        Optional<AntispamSection> sectionOpt = sectionResolver.resolve(event);
        if (sectionOpt.isEmpty()) {
            return HandlerResult.CONTINUE;
        }
        AntispamSection section = sectionOpt.get();

        List<AntispamRuleEntity> rules =
                ruleRepository.findByChannelIdAndStatusOrderByPriorityAsc(event.channelId(), "active");
        if (rules.isEmpty()) {
            return HandlerResult.CONTINUE;
        }

        Set<UUID> listIds = new HashSet<>();
        for (AntispamRuleEntity rule : rules) {
            var cfg = ru.posts.antispam.model.RuleConfig.fromMap(rule.getConfig());
            listIds.addAll(cfg.blackListIds());
            listIds.addAll(cfg.whiteListIds());
        }
        List<AntispamWordListEntity> wordLists = listIds.isEmpty()
                ? List.of()
                : wordListRepository.findByWorkspaceIdAndIdIn(event.workspaceId(), listIds);

        List<CompiledRule> compiled = ruleCache.getOrCompile(event.channelId(), rules, wordLists);
        EvaluationContext ctx = buildContext(event, compiled);
        AntispamEventView view = toView(event, section, ctx);

        Instant start = Instant.now();
        Verdict verdict = AntispamEngine.evaluate(view, compiled, ctx);
        Duration took = Duration.between(start, Instant.now());

        if (!verdict.matched()) {
            metrics.recordVerdict(event.network(), section.wire(), "none", took);
            return HandlerResult.CONTINUE;
        }

        String primaryReason =
                verdict.reasons().isEmpty() ? "matched" : verdict.reasons().getFirst();
        metrics.recordVerdict(event.network(), section.wire(), primaryReason, took);
        actionApplier.apply(event, section, verdict);

        // POST_SUGGESTED match must CONSUME so suggest module does not publish
        if (event.kind() == ru.posts.social.inbound.SocialEventKind.POST_SUGGESTED) {
            return HandlerResult.CONSUMED;
        }
        return HandlerResult.CONTINUE;
    }

    private EvaluationContext buildContext(SocialInboundEvent event, List<CompiledRule> compiled) {
        ChannelEntity channel =
                channelRepository.findByIdAndDeletedAtIsNull(event.channelId()).orElse(null);
        String communityId = channel == null ? null : channel.getExternalId();
        String authorId = event.author() == null ? null : event.author().id();

        boolean needsMember = compiled.stream().anyMatch(r -> r.config().filterNonMembers());
        boolean isMember = true;
        if (needsMember && communityId != null && authorId != null && "vk".equalsIgnoreCase(event.network())) {
            isMember = membershipCache.isMember(communityId, authorId, (g, u) -> true);
        }

        boolean needsRepeat = compiled.stream().anyMatch(r -> r.config().filterRepeat());
        boolean repeatHit = false;
        if (needsRepeat && authorId != null) {
            int hours = compiled.stream()
                    .filter(r -> r.config().filterRepeat())
                    .mapToInt(r -> r.config().repeatWindowHours())
                    .max()
                    .orElse(12);
            repeatHit = repeatDetector.isRepeat(
                    event.channelId(), authorId, event.text(), Duration.ofHours(Math.max(1, hours)));
        }

        boolean underAds = adsContextResolver.isUnderAds(event);
        boolean underPinned = pinnedPostCache.isUnderPinned(event.channelId(), postIdOf(event));
        boolean autoForward = isAutomaticForward(event);
        boolean senderChat = isSenderChat(event);
        boolean own = isOwnCommunity(event, communityId);

        Set<String> attachmentTypes = new HashSet<>();
        if (event.attachments() != null) {
            for (SocialInboundAttachment a : event.attachments()) {
                if (a.type() != null) {
                    attachmentTypes.add(a.type().toLowerCase(Locale.ROOT));
                }
            }
        }

        return new EvaluationContext(
                false,
                isMember,
                underAds,
                underPinned,
                autoForward,
                senderChat,
                own,
                repeatHit,
                communityId,
                Set.copyOf(attachmentTypes));
    }

    private AntispamEventView toView(SocialInboundEvent event, AntispamSection section, EvaluationContext ctx) {
        List<String> types = new ArrayList<>(ctx.attachmentTypes());
        return AntispamEventView.of(
                event.network(),
                section,
                event.author() == null ? null : event.author().id(),
                event.author() != null && event.author().community(),
                event.author() != null && event.author().bot(),
                event.text(),
                types,
                ctx.isAutomaticForward(),
                ctx.fromSenderChat());
    }

    private boolean isAutomaticForward(SocialInboundEvent event) {
        if (event.remoteIds() != null
                && "true".equalsIgnoreCase(event.remoteIds().get("is_automatic_forward"))) {
            return true;
        }
        JsonNode raw = readRaw(event.raw());
        JsonNode message = raw.has("message") ? raw.path("message") : raw.path("edited_message");
        return message.path("is_automatic_forward").asBoolean(false);
    }

    private boolean isSenderChat(SocialInboundEvent event) {
        if (event.remoteIds() != null && event.remoteIds().containsKey("sender_chat_id")) {
            return true;
        }
        JsonNode raw = readRaw(event.raw());
        JsonNode message = raw.has("message") ? raw.path("message") : raw.path("edited_message");
        return message.has("sender_chat") && !message.path("sender_chat").isNull();
    }

    private boolean isOwnCommunity(SocialInboundEvent event, String communityId) {
        if (communityId == null || event.author() == null) {
            return false;
        }
        String author = event.author().id();
        if (communityId.equals(author)) {
            return true;
        }
        if (communityId.startsWith("-") && communityId.substring(1).equals(author)) {
            return true;
        }
        return author != null && author.startsWith("-") && author.substring(1).equals(communityId.replace("-", ""));
    }

    private String postIdOf(SocialInboundEvent event) {
        if (event.remoteIds() != null && event.remoteIds().get("post_id") != null) {
            return event.remoteIds().get("post_id");
        }
        return null;
    }

    private JsonNode readRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            log.debug("Failed to parse antispam raw payload: {}", ex.toString());
            return objectMapper.nullNode();
        }
    }
}
