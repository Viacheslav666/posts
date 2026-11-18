package ru.posts.ord.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats the mandatory Russian ad marker line and applies one of six placement modes (BE-25).
 */
public final class AdMarker {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"')\\]]+");

    private AdMarker() {}

    /** Result of applying a placement mode to post/story text. */
    public record ApplyResult(String text, String linkUrl, Map<String, Object> meta, List<String> warnings) {
        public ApplyResult {
            meta = meta == null ? Map.of() : Map.copyOf(meta);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    /** Returns {@code Реклама. {advertiserName}. ИНН {inn}. erid: {erid}}. */
    public static String format(String advertiserName, String inn, String erid) {
        String name = advertiserName == null ? "" : advertiserName.trim();
        String innValue = inn == null ? "" : inn.trim();
        String eridValue = erid == null ? "" : erid.trim();
        return "Реклама. " + name + ". ИНН " + innValue + ". erid: " + eridValue;
    }

    /** Prepends {@code marker} to {@code text} with a newline when both are non-blank. */
    public static String prepend(String text, String marker) {
        boolean hasMarker = marker != null && !marker.isBlank();
        boolean hasText = text != null && !text.isBlank();
        if (!hasMarker) {
            return text == null ? "" : text;
        }
        if (!hasText) {
            return marker;
        }
        return marker + "\n" + text;
    }

    /** Appends {@code marker} after {@code text} with a newline when both are non-blank. */
    public static String append(String text, String marker) {
        boolean hasMarker = marker != null && !marker.isBlank();
        boolean hasText = text != null && !text.isBlank();
        if (!hasMarker) {
            return text == null ? "" : text;
        }
        if (!hasText) {
            return marker;
        }
        return text + "\n" + marker;
    }

    /**
     * Applies placement. When text already contains {@code erid:}, returns it unchanged (idempotent).
     *
     * @param network {@code vk|tg|ok|max|…} — used for VK menu meta hints
     * @param linkUrl optional story CTA URL (erid injected for stories / click modes)
     */
    public static ApplyResult apply(
            String text, String marker, String erid, MarkingPlacement placement, String network, String linkUrl) {
        List<String> warnings = new ArrayList<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        MarkingPlacement mode = placement == null ? MarkingPlacement.text_start : placement;
        meta.put("markingPlacement", mode.name());

        if (text != null && text.contains("erid:")) {
            return new ApplyResult(text, linkUrl, meta, warnings);
        }

        String eridValue = erid == null ? "" : erid.trim();
        String workingText = text == null ? "" : text;
        String workingLink = linkUrl;

        if (mode.requestsVkMenu()) {
            warnings.add(MarkingPlacement.VK_MENU_FALLBACK_WARNING);
            meta.put("vkMarkAsAds", true);
            meta.put("placementFallback", "text_end");
            if ("vk".equalsIgnoreCase(network)) {
                meta.put("vk_wall_mark_as_ads", 1);
            }
            workingText = append(workingText, marker);
            return new ApplyResult(workingText, workingLink, meta, warnings);
        }

        if (mode.isClickLink()) {
            UrlInjectResult injected = injectEridIntoUrls(workingText, eridValue);
            workingText = injected.text();
            boolean any = injected.changed();
            if (workingLink != null && !workingLink.isBlank()) {
                String nextLink = appendEridQuery(workingLink, eridValue);
                if (!nextLink.equals(workingLink)) {
                    workingLink = nextLink;
                    any = true;
                }
            }
            if (!any) {
                warnings.add("click_link_fallback: no external URLs found; using text marker");
                workingText = mode == MarkingPlacement.click_link_start
                        ? prepend(workingText, marker)
                        : append(workingText, marker);
                meta.put("placementFallback", mode == MarkingPlacement.click_link_start ? "text_start" : "text_end");
            }
            return new ApplyResult(workingText, workingLink, meta, warnings);
        }

        workingText = switch (mode) {
            case text_end -> append(workingText, marker);
            case text_start -> prepend(workingText, marker);
            default -> prepend(workingText, marker);
        };
        return new ApplyResult(workingText, workingLink, meta, warnings);
    }

    /** Story helper: inject erid into link URL and ensure marker appears in description text. */
    public static ApplyResult applyForStory(
            String text, String marker, String erid, MarkingPlacement placement, String linkUrl) {
        MarkingPlacement mode = placement == null ? MarkingPlacement.text_end : placement;
        List<String> warnings = new ArrayList<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("markingPlacement", mode.name());
        meta.put("story", true);

        String workingText = text == null ? "" : text;
        String workingLink = linkUrl;
        String eridValue = erid == null ? "" : erid.trim();

        if (workingLink != null && !workingLink.isBlank()) {
            workingLink = appendEridQuery(workingLink, eridValue);
        } else {
            warnings.add("story_link_missing: erid appended to story text instead of link_url");
        }

        if (workingText == null || !workingText.contains("erid:")) {
            // Stories: prefer end of description; vk_menu still falls back to text.
            if (mode == MarkingPlacement.text_start || mode == MarkingPlacement.click_link_start) {
                workingText = prepend(workingText, marker);
            } else {
                if (mode.requestsVkMenu()) {
                    warnings.add(MarkingPlacement.VK_MENU_FALLBACK_WARNING);
                    meta.put("placementFallback", "text_end");
                }
                workingText = append(workingText, marker);
            }
        }
        return new ApplyResult(workingText, workingLink, meta, warnings);
    }

    public static String appendEridQuery(String url, String erid) {
        if (url == null || url.isBlank() || erid == null || erid.isBlank()) {
            return url;
        }
        if (url.toLowerCase(Locale.ROOT).contains("erid=")) {
            return url;
        }
        int hash = url.indexOf('#');
        String base = hash >= 0 ? url.substring(0, hash) : url;
        String fragment = hash >= 0 ? url.substring(hash) : "";
        char sep = base.contains("?") ? '&' : '?';
        return base + sep + "erid=" + erid + fragment;
    }

    private record UrlInjectResult(String text, boolean changed) {}

    private static UrlInjectResult injectEridIntoUrls(String text, String erid) {
        if (text == null || text.isBlank() || erid == null || erid.isBlank()) {
            return new UrlInjectResult(text == null ? "" : text, false);
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String url = matcher.group();
            // Trim common trailing punctuation already excluded by pattern, but keep raw.
            String next = appendEridQuery(url, erid);
            if (!next.equals(url)) {
                changed = true;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(next));
        }
        matcher.appendTail(sb);
        return new UrlInjectResult(sb.toString(), changed);
    }
}
