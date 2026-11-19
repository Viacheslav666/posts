package ru.posts.crosspost.filter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.model.CrosspostCandidate;
import ru.posts.crosspost.text.CrosspostTextSignals;
import ru.posts.grabber.schedule.WorkingWindow;

/**
 * Pure filter engine. Returns stable machine reason codes (empty = pass).
 *
 * <p>Ad posts (erid / «Реклама») are excluded by default ({@code ad_post_excluded}).
 */
public final class CrosspostFilterEngine {

    private CrosspostFilterEngine() {}

    public static List<String> evaluate(CrosspostCandidate candidate, Map<String, Object> config) {
        return evaluate(candidate, config, Instant.now());
    }

    public static List<String> evaluate(CrosspostCandidate candidate, Map<String, Object> config, Instant now) {
        List<String> reasons = new ArrayList<>();
        if (candidate == null) {
            reasons.add("empty_candidate");
            return reasons;
        }
        String window = WorkingWindow.skipReasonIfClosed(CrosspostConfigSupport.withDefaults(config), now);
        if (window != null) {
            reasons.add(window);
            return reasons;
        }

        Map<String, Object> filters = CrosspostConfigSupport.filters(config);
        String text = candidate.text() == null ? "" : candidate.text();

        if (CrosspostConfigSupport.bool(filters.get("exclude_ads"), true)
                && CrosspostTextSignals.looksLikeAd(text, candidate.markedAsAds(), candidate.hasErid())) {
            reasons.add("ad_post_excluded");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_reposts"), false) && candidate.isRepost()) {
            reasons.add("exclude_reposts");
        }
        if (!CrosspostConfigSupport.bool(filters.get("include_reposts"), true) && candidate.isRepost()) {
            reasons.add("exclude_reposts");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_links"), false)
                && (CrosspostTextSignals.containsLink(text) || candidate.hasLinkAttachment())) {
            reasons.add("exclude_links");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_snippets"), true) && candidate.hasSnippet()) {
            reasons.add("exclude_snippets");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_articles"), true) && candidate.hasArticle()) {
            reasons.add("exclude_articles");
        }
        if (CrosspostConfigSupport.bool(filters.get("skip_if_no_text"), false)
                && CrosspostTextSignals.isBlankText(text)) {
            reasons.add("skip_if_no_text");
        }
        if (CrosspostConfigSupport.bool(filters.get("skip_if_no_images"), false) && !candidate.hasImage()) {
            reasons.add("skip_if_no_images");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_video"), false) && candidate.hasVideo()) {
            reasons.add("exclude_video");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_clips"), true) && candidate.hasClip()) {
            reasons.add("exclude_clips");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_poll"), true) && candidate.hasPoll()) {
            reasons.add("exclude_poll");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_geo"), true) && candidate.hasGeo()) {
            reasons.add("exclude_geo");
        }
        if (CrosspostConfigSupport.bool(filters.get("exclude_audio"), true) && candidate.hasAudio()) {
            reasons.add("exclude_audio");
        }

        List<String> include = CrosspostConfigSupport.stringList(filters.get("keyword_include"));
        if (!include.isEmpty()) {
            String lower = text.toLowerCase(Locale.ROOT);
            boolean hit = include.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(lower::contains);
            if (!hit) {
                reasons.add("keyword_include");
            }
        }
        List<String> exclude = CrosspostConfigSupport.stringList(filters.get("keyword_exclude"));
        if (!exclude.isEmpty()) {
            String lower = text.toLowerCase(Locale.ROOT);
            boolean hit = exclude.stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(lower::contains);
            if (hit) {
                reasons.add("keyword_exclude");
            }
        }
        return reasons;
    }

    /** First reason or null when candidate passes. */
    public static String firstReason(CrosspostCandidate candidate, Map<String, Object> config) {
        List<String> reasons = evaluate(candidate, config);
        return reasons.isEmpty() ? null : reasons.getFirst();
    }
}
