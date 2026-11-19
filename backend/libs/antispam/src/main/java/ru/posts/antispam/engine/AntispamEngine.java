package ru.posts.antispam.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import ru.posts.antispam.model.AntispamAction;
import ru.posts.antispam.model.AntispamEventView;
import ru.posts.antispam.model.CompiledRule;
import ru.posts.antispam.model.EvaluationContext;
import ru.posts.antispam.model.RuleConfig;
import ru.posts.antispam.model.Verdict;

/**
 * Pure antispam evaluation. Rules must be pre-sorted by priority ascending (first match wins).
 * Target: p99 &lt; 5 ms with compiled Aho-Corasick matchers.
 */
public final class AntispamEngine {

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[^\\s<>\\[\\]()\"']+|vk\\.cc/\\S+|t\\.me/\\S+)");
    private static final Pattern VK_URL =
            Pattern.compile("(?i)\\b(?:https?://)?(?:m\\.)?(?:vk\\.com|vk\\.ru|vk\\.cc)/\\S+");

    private AntispamEngine() {}

    public static Verdict evaluate(
            AntispamEventView event, List<CompiledRule> rules, EvaluationContext context) {
        if (event == null || rules == null || rules.isEmpty()) {
            return Verdict.none();
        }
        EvaluationContext ctx = context == null ? EvaluationContext.empty() : context;

        // Hard exclusions — never touch admins / whitelist / own community / auto-forwards
        if (ctx.isAutomaticForward() || event.isAutomaticForward()) {
            return Verdict.none();
        }

        for (CompiledRule rule : rules) {
            if (rule.sections() != null
                    && !rule.sections().isEmpty()
                    && !rule.sections().contains(event.section().wire())) {
                continue;
            }
            if (isExcluded(event, rule.config(), ctx)) {
                continue;
            }
            List<String> reasons = matchReasons(event, rule, ctx);
            if (reasons.isEmpty()) {
                continue;
            }
            List<AntispamAction> actions = buildActions(rule, reasons);
            return Verdict.hit(rule.ruleId(), rule.name(), rule.dryRun(), reasons, actions);
        }
        return Verdict.none();
    }

    static boolean isExcluded(AntispamEventView event, RuleConfig cfg, EvaluationContext ctx) {
        if (ctx.authorIsAdmin()) {
            return true;
        }
        if (cfg.adminIds() != null && event.authorId() != null && cfg.adminIds().contains(event.authorId())) {
            return true;
        }
        if (cfg.userWhitelist() != null
                && event.authorId() != null
                && cfg.userWhitelist().contains(event.authorId())) {
            return true;
        }
        if (cfg.excludeOwnCommunity() && (ctx.ownCommunityOrChannel() || isOwnAuthor(event, ctx))) {
            return true;
        }
        return false;
    }

    private static boolean isOwnAuthor(AntispamEventView event, EvaluationContext ctx) {
        if (ctx.communityOrChannelId() == null || event.authorId() == null) {
            return false;
        }
        String own = ctx.communityOrChannelId();
        String author = event.authorId();
        if (own.equals(author)) {
            return true;
        }
        // VK communities are negative ids; compare absolute forms
        if (own.startsWith("-") && own.substring(1).equals(author)) {
            return true;
        }
        if (author.startsWith("-") && author.substring(1).equals(own)) {
            return true;
        }
        return false;
    }

    static List<String> matchReasons(AntispamEventView event, CompiledRule rule, EvaluationContext ctx) {
        RuleConfig cfg = rule.config();
        List<String> reasons = new ArrayList<>();
        String text = event.text();

        // White keywords short-circuit (message allowed)
        if (rule.whiteMatcher() != null && rule.whiteMatcher().containsAny(text)) {
            return List.of();
        }

        if (rule.blackMatcher() != null) {
            var hits = rule.blackMatcher().findAll(text);
            if (!hits.isEmpty()) {
                reasons.add("keyword:" + hits.getFirst().original());
            }
        }

        if (cfg.filterAllLinks() && containsLink(text)) {
            if (!isDomainWhitelisted(text, cfg.domainWhitelist())) {
                reasons.add("link");
            }
        }
        if (cfg.filterVkLinks() && VK_URL.matcher(text).find()) {
            if (!isDomainWhitelisted(text, cfg.domainWhitelist())) {
                reasons.add("vk_link");
            }
        }

        if (cfg.filterPhoto() && hasAttachment(event, "photo")) {
            reasons.add("attachment:photo");
        }
        if (cfg.filterPhotoWithoutText() && hasAttachment(event, "photo") && !event.hasText()) {
            reasons.add("attachment:photo_without_text");
        }
        if (cfg.filterVideo() && (hasAttachment(event, "video") || hasAttachment(event, "clip"))) {
            reasons.add("attachment:video");
        }
        if (cfg.filterAudio() && (hasAttachment(event, "audio") || hasAttachment(event, "voice"))) {
            reasons.add("attachment:audio");
        }
        if (cfg.filterDoc() && (hasAttachment(event, "doc") || hasAttachment(event, "document"))) {
            reasons.add("attachment:doc");
        }
        if (cfg.filterGif() && (hasAttachment(event, "gif") || hasAttachment(event, "animation"))) {
            reasons.add("attachment:gif");
        }
        if (cfg.filterSticker() && hasAttachment(event, "sticker")) {
            reasons.add("attachment:sticker");
        }

        if (cfg.filterCaps() && capsRatio(text, cfg.capsMinLetters()) >= cfg.capsRatioThreshold()) {
            reasons.add("caps");
        }

        if (cfg.filterFromCommunities() && event.authorCommunity()) {
            reasons.add("from_community");
        }
        if (cfg.filterSenderChat() && (event.fromSenderChat() || ctx.fromSenderChat())) {
            reasons.add("sender_chat");
        }
        if (cfg.filterNonMembers() && !ctx.authorIsMember()) {
            reasons.add("non_member");
        }
        if (cfg.filterUnderAds() && ctx.underAds()) {
            reasons.add("under_ads");
        }
        if (cfg.filterUnderPinned() && ctx.underPinned()) {
            reasons.add("under_pinned");
        }
        if (cfg.filterRepeat() && ctx.repeatHit()) {
            reasons.add("repeat");
        }

        return reasons;
    }

    static List<AntispamAction> buildActions(CompiledRule rule, List<String> reasons) {
        if (rule.dryRun()) {
            return List.of(AntispamAction.flag());
        }
        RuleConfig cfg = rule.config();
        List<AntispamAction> actions = new ArrayList<>();
        if (cfg.replyEnabled()) {
            actions.add(AntispamAction.reply(cfg.replyTemplate()));
        }
        if (cfg.deleteEnabled()) {
            actions.add(AntispamAction.delete());
        }
        if (cfg.banEnabled()) {
            actions.add(AntispamAction.ban(cfg.banDays(), cfg.banReason(), cfg.banCommentVisible()));
        }
        if (actions.isEmpty()) {
            actions.add(AntispamAction.flag());
        }
        return actions;
    }

    static boolean containsLink(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("vk.cc/") || lower.contains("t.me/") || lower.contains("vk.com/")) {
            return true;
        }
        return URL.matcher(text).find();
    }

    static boolean isDomainWhitelisted(String text, java.util.Set<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty() || text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String domain : whitelist) {
            if (domain != null && !domain.isBlank() && lower.contains(domain.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAttachment(AntispamEventView event, String type) {
        if (event.attachmentTypes() == null) {
            return false;
        }
        return event.attachmentTypes().contains(type);
    }

    static double capsRatio(String text, int minLetters) {
        if (text == null || text.isBlank()) {
            return 0d;
        }
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            letters++;
            if (Character.isUpperCase(cp)) {
                upper++;
            }
        }
        if (letters < minLetters) {
            return 0d;
        }
        return (double) upper / (double) letters;
    }
}
