package ru.posts.antispam.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Parsed {@code antispam_rules.config} JSON. */
public record RuleConfig(
        List<String> blackWords,
        List<String> whiteWords,
        List<UUID> blackListIds,
        List<UUID> whiteListIds,
        boolean keywordWholeWord,
        boolean filterAllLinks,
        boolean filterVkLinks,
        Set<String> domainWhitelist,
        boolean filterPhoto,
        boolean filterPhotoWithoutText,
        boolean filterVideo,
        boolean filterAudio,
        boolean filterDoc,
        boolean filterGif,
        boolean filterSticker,
        boolean filterCaps,
        double capsRatioThreshold,
        int capsMinLetters,
        boolean filterRepeat,
        int repeatWindowHours,
        boolean filterFromCommunities,
        boolean filterNonMembers,
        boolean filterUnderAds,
        boolean filterUnderPinned,
        boolean filterSenderChat,
        boolean deleteEnabled,
        boolean banEnabled,
        int banDays,
        String banReason,
        boolean banCommentVisible,
        boolean replyEnabled,
        String replyTemplate,
        Set<String> adminIds,
        Set<String> userWhitelist,
        boolean excludeOwnCommunity) {

    public static final String DEFAULT_REPLY = "Ваше сообщение удалено: {reasons}";

    @SuppressWarnings("unchecked")
    public static RuleConfig fromMap(Map<String, Object> raw) {
        Map<String, Object> cfg = raw == null ? Map.of() : raw;
        Map<String, Object> filters = map(cfg.get("filters"));
        Map<String, Object> keywords = map(filters.get("keywords"));
        Map<String, Object> links = map(filters.get("links"));
        Map<String, Object> attachments = map(filters.get("attachments"));
        Map<String, Object> caps = map(filters.get("caps"));
        Map<String, Object> repeat = map(filters.get("repeat"));
        Map<String, Object> authors = map(filters.get("authors"));
        Map<String, Object> context = map(filters.get("context"));
        Map<String, Object> actions = map(cfg.get("actions"));
        Map<String, Object> ban = map(actions.get("ban"));
        Map<String, Object> reply = map(actions.get("reply"));
        Map<String, Object> exceptions = map(cfg.get("exceptions"));

        return new RuleConfig(
                stringList(keywords.get("black")),
                stringList(keywords.get("white")),
                uuidList(keywords.get("black_list_ids")),
                uuidList(keywords.get("white_list_ids")),
                keywordWholeWord(keywords.get("match")),
                bool(links.get("all"), false),
                bool(links.get("vk"), false),
                stringSet(links.get("domain_whitelist")),
                bool(attachments.get("photo"), false),
                bool(attachments.get("photo_without_text"), false),
                bool(attachments.get("video"), false),
                bool(attachments.get("audio"), false),
                bool(attachments.get("doc"), false) || bool(attachments.get("document"), false),
                bool(attachments.get("gif"), false),
                bool(attachments.get("sticker"), false),
                bool(caps.get("enabled"), false),
                dbl(caps.get("ratio"), 0.7),
                integer(caps.get("min_letters"), 5),
                bool(repeat.get("enabled"), false),
                integer(repeat.get("window_hours"), 12),
                bool(authors.get("communities"), false),
                bool(authors.get("non_members"), false),
                bool(context.get("under_ads"), false),
                bool(context.get("under_pinned"), false),
                bool(authors.get("sender_chat"), false),
                bool(actions.get("delete"), true),
                bool(ban.get("enabled"), false),
                integer(ban.get("days"), 7),
                str(ban.get("reason"), "spam"),
                bool(ban.get("comment_visible"), false),
                bool(reply.get("enabled"), false),
                str(reply.get("template"), DEFAULT_REPLY),
                stringSet(exceptions.get("admin_ids")),
                stringSet(exceptions.get("user_whitelist")),
                bool(exceptions.get("exclude_own_community"), true));
    }

    public List<String> mergedBlack(Collection<String> fromLists) {
        LinkedHashSet<String> out = new LinkedHashSet<>(blackWords);
        if (fromLists != null) {
            out.addAll(fromLists);
        }
        return List.copyOf(out);
    }

    public List<String> mergedWhite(Collection<String> fromLists) {
        LinkedHashSet<String> out = new LinkedHashSet<>(whiteWords);
        if (fromLists != null) {
            out.addAll(fromLists);
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static List<String> stringList(Object o) {
        if (o instanceof Collection<?> c) {
            List<String> out = new ArrayList<>();
            for (Object item : c) {
                if (item != null && !item.toString().isBlank()) {
                    out.add(item.toString());
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }

    private static Set<String> stringSet(Object o) {
        return Set.copyOf(stringList(o));
    }

    private static List<UUID> uuidList(Object o) {
        if (o instanceof Collection<?> c) {
            List<UUID> out = new ArrayList<>();
            for (Object item : c) {
                if (item == null) {
                    continue;
                }
                try {
                    out.add(UUID.fromString(item.toString()));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    private static int integer(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static double dbl(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    /** {@code filters.keywords.match}: {@code whole_word} or {@code substring} (default). */
    private static boolean keywordWholeWord(Object o) {
        return "whole_word".equalsIgnoreCase(str(o, "substring"));
    }
}
