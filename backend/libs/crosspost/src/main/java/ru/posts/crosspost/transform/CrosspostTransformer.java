package ru.posts.crosspost.transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.model.CrosspostCandidate;
import ru.posts.crosspost.text.CrosspostTextSignals;

/** Content transforms for target networks (formatting, links, length limits). */
public final class CrosspostTransformer {

    public static final int TG_CAPTION_MAX = 1024;
    public static final int TG_TEXT_MAX = 4096;
    public static final int VK_TEXT_MAX = 16384;
    public static final int OK_TEXT_MAX = 16384;
    public static final int MAX_TEXT_MAX = 4000;

    private CrosspostTransformer() {}

    public record TransformResult(
            String text,
            String overflowText,
            List<Map<String, Object>> attachments,
            Map<String, Object> options,
            List<String> notes) {}

    public static TransformResult transform(
            CrosspostCandidate candidate,
            Map<String, Object> ruleConfig,
            Map<String, Object> targetOptions,
            String targetNetwork,
            String sourcePermalink) {
        Map<String, Object> transform = CrosspostConfigSupport.transform(ruleConfig);
        Map<String, Object> merged = new LinkedHashMap<>(transform);
        if (targetOptions != null) {
            merged.putAll(targetOptions);
        }

        String text = candidate.text() == null ? "" : candidate.text();
        List<String> notes = new ArrayList<>();
        if (CrosspostConfigSupport.bool(merged.get("remove_links"), false)) {
            text = CrosspostTextSignals.removeLinks(text);
            notes.add("links_removed");
        }
        if (CrosspostConfigSupport.bool(merged.get("remove_hashtags"), false)) {
            text = CrosspostTextSignals.removeHashtags(text);
            notes.add("hashtags_removed");
        }
        if (CrosspostConfigSupport.bool(merged.get("add_source_link"), false)
                && sourcePermalink != null
                && !sourcePermalink.isBlank()) {
            if (!text.contains(sourcePermalink)) {
                text = text.isBlank() ? sourcePermalink : text + "\n\n" + sourcePermalink;
                notes.add("source_link_added");
            }
        }

        List<Map<String, Object>> attachments = new ArrayList<>();
        for (Map<String, Object> att : candidate.attachments()) {
            String type = String.valueOf(att.getOrDefault("type", ""));
            if ("audio".equals(type) && "vk".equals(candidate.network())) {
                notes.add("vk_audio_skipped");
                continue;
            }
            if (("video".equals(type) || "clip".equals(type)) && "vk".equals(candidate.network())) {
                if (CrosspostConfigSupport.bool(merged.get("vk_video_as_link"), true)) {
                    Object url = att.get("url");
                    if (url != null && !url.toString().isBlank()) {
                        text = text.isBlank() ? url.toString() : text + "\n" + url;
                        notes.add("vk_video_as_link");
                    } else {
                        notes.add("vk_video_skipped");
                    }
                } else {
                    notes.add("vk_video_skipped");
                }
                continue;
            }
            attachments.add(new LinkedHashMap<>(att));
        }

        Map<String, Object> options = new LinkedHashMap<>();
        if (CrosspostConfigSupport.bool(merged.get("close_comments"), false)
                && ("vk".equals(targetNetwork) || "ok".equals(targetNetwork))) {
            options.put("closeComments", true);
            notes.add("close_comments");
        }
        if (CrosspostConfigSupport.bool(merged.get("publish_as_vk_repost"), false) && "vk".equals(targetNetwork)) {
            options.put("publishAsRepost", true);
            notes.add("publish_as_vk_repost");
        }

        String overflow = null;
        String mode = String.valueOf(merged.getOrDefault("long_text_mode", "split"));
        if ("tg".equals(targetNetwork)) {
            boolean hasMedia = !attachments.isEmpty();
            int primaryMax = hasMedia ? TG_CAPTION_MAX : TG_TEXT_MAX;
            if (text.length() > primaryMax) {
                if ("truncate_read_more".equals(mode)) {
                    text = CrosspostTextSignals.truncateWithReadMore(text, primaryMax, sourcePermalink);
                    notes.add("tg_truncated_read_more");
                } else if (hasMedia) {
                    // Media with caption + remainder as follow-up message in same publication job.
                    overflow = text.substring(TG_CAPTION_MAX);
                    text = text.substring(0, TG_CAPTION_MAX);
                    notes.add("tg_split_caption_overflow");
                    if (overflow.length() > TG_TEXT_MAX) {
                        overflow = overflow.substring(0, TG_TEXT_MAX);
                        notes.add("tg_overflow_truncated");
                    }
                } else {
                    overflow = text.substring(TG_TEXT_MAX);
                    text = text.substring(0, TG_TEXT_MAX);
                    notes.add("tg_split_text_overflow");
                }
            }
        } else {
            int max =
                    switch (targetNetwork == null ? "" : targetNetwork) {
                        case "vk" -> VK_TEXT_MAX;
                        case "ok" -> OK_TEXT_MAX;
                        case "max" -> MAX_TEXT_MAX;
                        default -> VK_TEXT_MAX;
                    };
            if (text.length() > max) {
                text = CrosspostTextSignals.truncateWithReadMore(text, max, sourcePermalink);
                notes.add(targetNetwork + "_truncated");
            }
        }

        if (overflow != null && !overflow.isBlank()) {
            options.put("followUpText", overflow);
        }
        return new TransformResult(text, overflow, List.copyOf(attachments), options, List.copyOf(notes));
    }
}
