package ru.posts.suggest.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.stereotype.Component;
import ru.posts.content.hash.SimHasher;
import ru.posts.suggest.config.SuggestConfigSupport;

/**
 * Pure-ish filter evaluation for VK suggested posts (BE-34). Tries are cached by
 * configVersion+keyword set.
 */
@Component
public class SuggestFilterEngine {

    private final ConcurrentHashMap<String, Trie> trieCache = new ConcurrentHashMap<>();

    public SuggestDecision evaluate(SuggestItemView item, Map<String, Object> config, SuggestFilterContext ctx) {
        Map<String, Object> cfg = SuggestConfigSupport.withDefaults(config);
        Map<String, Object> filters = SuggestConfigSupport.filters(cfg);
        List<SuggestReason> hard = new ArrayList<>();
        List<SuggestReason> soft = new ArrayList<>();

        String authorId = item.authorVkId() == null ? "" : item.authorVkId();
        List<String> whitelist = SuggestConfigSupport.stringList(filters.get("author_whitelist"));
        List<String> blacklist = SuggestConfigSupport.stringList(filters.get("author_blacklist"));

        boolean whitelisted = !whitelist.isEmpty() && whitelist.contains(authorId);
        if (!blacklist.isEmpty() && blacklist.contains(authorId)) {
            hard.add(SuggestReason.hard("author_blacklist", "Автор в чёрном списке"));
        }

        if (whitelisted) {
            // Whitelist bypasses content filters (still respect blacklist above).
            return decide(ctx.mode(), hard, soft);
        }

        String text = item.text() == null ? "" : item.text();
        String normalized = SuggestConfigSupport.normalizeText(text);
        List<Map<String, Object>> attachments = item.attachments() == null ? List.of() : item.attachments();
        int configVersion = SuggestConfigSupport.intVal(cfg.get("config_version"), 1);

        List<String> stopWords = SuggestConfigSupport.stringList(filters.get("stop_words"));
        if (!stopWords.isEmpty() && matchesKeywords(normalized, stopWords, configVersion, "stop", matchMode(filters))) {
            hard.add(SuggestReason.hard("stop_words", "Текст содержит стоп-слово"));
        }

        List<String> exclude = SuggestConfigSupport.stringList(filters.get("keyword_exclude"));
        if (!exclude.isEmpty()
                && matchesKeywords(normalized, exclude, configVersion, "exclude", matchMode(filters))) {
            hard.add(SuggestReason.hard("keyword_exclude", "Текст содержит запрещённое ключевое слово"));
        }

        List<String> include = SuggestConfigSupport.stringList(filters.get("keyword_include"));
        if (!include.isEmpty()
                && !matchesKeywords(normalized, include, configVersion, "include", matchMode(filters))) {
            hard.add(SuggestReason.hard("keyword_include", "Нет обязательного ключевого слова"));
        }

        if (SuggestConfigSupport.bool(filters.get("require_text"), false) && isBlankText(text)) {
            hard.add(SuggestReason.hard("require_text", "Требуется текст"));
        }
        if (SuggestConfigSupport.bool(filters.get("require_image"), false) && !hasImage(attachments)) {
            hard.add(SuggestReason.hard("require_image", "Требуется изображение"));
        }

        List<String> forbid = SuggestConfigSupport.stringList(filters.get("forbid_attachments"));
        if (!forbid.isEmpty()) {
            for (String type : presentTypes(attachments)) {
                if (forbid.stream().anyMatch(f -> f.equalsIgnoreCase(type))) {
                    hard.add(SuggestReason.hard(
                            "forbid_attachments", "Запрещённый тип вложения: " + type));
                    break;
                }
            }
        }

        if (SuggestConfigSupport.bool(filters.get("require_messages_allowed"), false)) {
            if (ctx.authorMessagesAllowed() != null && !ctx.authorMessagesAllowed()) {
                hard.add(SuggestReason.hard(
                        "require_messages_allowed", "Автор не разрешил сообщения от сообщества"));
            }
        }
        if (SuggestConfigSupport.bool(filters.get("forbid_closed_profile"), false)) {
            if (Boolean.TRUE.equals(ctx.authorClosedProfile())) {
                hard.add(SuggestReason.hard("forbid_closed_profile", "Закрытый профиль автора"));
            }
        }

        Map<String, Object> limits = SuggestConfigSupport.limits(cfg);
        Map<String, Object> perAuthor = SuggestConfigSupport.map(limits.get("per_author"));
        Map<String, Object> global = SuggestConfigSupport.map(limits.get("global"));
        checkLimit(hard, "author_limit_hour", "Лимит автора за час", perAuthor.get("hour"), ctx.authorPublishCounts().hour());
        checkLimit(hard, "author_limit_day", "Лимит автора за сутки", perAuthor.get("day"), ctx.authorPublishCounts().day());
        checkLimit(hard, "author_limit_week", "Лимит автора за неделю", perAuthor.get("week"), ctx.authorPublishCounts().week());
        checkLimit(hard, "global_limit_hour", "Глобальный лимит за час", global.get("hour"), ctx.globalPublishCounts().hour());
        checkLimit(hard, "global_limit_day", "Глобальный лимит за сутки", global.get("day"), ctx.globalPublishCounts().day());
        checkLimit(hard, "global_limit_week", "Глобальный лимит за неделю", global.get("week"), ctx.globalPublishCounts().week());

        Map<String, Object> adsPause = SuggestConfigSupport.adsPause(cfg);
        if (SuggestConfigSupport.bool(adsPause.get("enabled"), true) && ctx.lastWallWasAds()) {
            // Soft: scheduler delays; hard filter-out only when pause_after_ads handled as filter
            soft.add(SuggestReason.soft("ads_pause", "Пауза после рекламного поста"));
        }

        if (SuggestConfigSupport.dedupEnabled(cfg) && item.simhash() != null) {
            double threshold = SuggestConfigSupport.similarityThreshold(cfg);
            for (Long recent : ctx.recentSimhashes()) {
                if (recent == null) {
                    continue;
                }
                double sim = SimHasher.similarity(item.simhash(), recent);
                if (sim + 1e-9 >= threshold) {
                    hard.add(SuggestReason.hard("duplicate_content", "Дубликат контента (simhash)"));
                    break;
                }
                if (threshold > 0.05 && sim + 1e-9 >= threshold - 0.05) {
                    soft.add(SuggestReason.soft(
                            "near_duplicate",
                            String.format(Locale.ROOT, "Похожий контент (%.0f%%)", sim * 100)));
                }
            }
        }

        return decide(ctx.mode(), hard, soft);
    }

    private SuggestDecision decide(String mode, List<SuggestReason> hard, List<SuggestReason> soft) {
        List<SuggestReason> all = new ArrayList<>(hard.size() + soft.size());
        all.addAll(hard);
        all.addAll(soft);
        if (!hard.isEmpty()) {
            // stop_words / keyword → REJECT; limits/blacklist/dedup → FILTER_OUT
            boolean reject =
                    hard.stream().anyMatch(r -> r.code().equals("stop_words") || r.code().equals("keyword_exclude"));
            return SuggestDecision.of(reject ? SuggestAction.REJECT : SuggestAction.FILTER_OUT, all);
        }
        String m = mode == null ? "auto" : mode.toLowerCase(Locale.ROOT);
        if ("manual".equals(m)) {
            return SuggestDecision.of(SuggestAction.MODERATE, all);
        }
        if ("hybrid".equals(m) && !soft.isEmpty()) {
            return SuggestDecision.of(SuggestAction.MODERATE, all);
        }
        return SuggestDecision.of(SuggestAction.PUBLISH, all);
    }

    private static void checkLimit(
            List<SuggestReason> hard, String code, String message, Object limitRaw, long current) {
        int limit = SuggestConfigSupport.intVal(limitRaw, 0);
        if (limit > 0 && current >= limit) {
            hard.add(SuggestReason.hard(code, message));
        }
    }

    private boolean matchesKeywords(
            String normalizedText, List<String> keywords, int configVersion, String kind, String matchMode) {
        List<String> needles = keywords.stream()
                .filter(Objects::nonNull)
                .map(SuggestConfigSupport::normalizeText)
                .filter(k -> !k.isBlank())
                .toList();
        if (needles.isEmpty() || normalizedText.isBlank()) {
            return false;
        }
        Trie trie = trieCache.computeIfAbsent(cacheKey(configVersion, kind, needles), k -> buildTrie(needles));
        Collection<Emit> emits = trie.parseText(normalizedText);
        if (emits.isEmpty()) {
            return false;
        }
        if (SuggestConfigSupport.KEYWORD_WHOLE_WORD.equalsIgnoreCase(matchMode)) {
            for (Emit emit : emits) {
                if (isWholeWord(normalizedText, emit.getStart(), emit.getEnd())) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static Trie buildTrie(List<String> needles) {
        Trie.TrieBuilder builder = Trie.builder().ignoreOverlaps();
        for (String n : needles) {
            builder.addKeyword(n);
        }
        return builder.build();
    }

    private static String cacheKey(int configVersion, String kind, List<String> needles) {
        return configVersion + "|" + kind + "|" + needles.stream().sorted().collect(Collectors.joining("\u0001"));
    }

    /** Emit end is inclusive in ahocorasick 0.6.x. */
    static boolean isWholeWord(String text, int start, int endInclusive) {
        if (start > 0 && isWordChar(text.charAt(start - 1))) {
            return false;
        }
        int after = endInclusive + 1;
        if (after < text.length() && isWordChar(text.charAt(after))) {
            return false;
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == 'ё' || c == 'Ё';
    }

    private static String matchMode(Map<String, Object> filters) {
        return SuggestConfigSupport.string(filters.get("keyword_match"), SuggestConfigSupport.KEYWORD_CONTAINS);
    }

    private static boolean isBlankText(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        // strip common emoji / whitespace-only
        String stripped = text.replaceAll("\\p{So}+", "").trim();
        return stripped.isBlank();
    }

    private static boolean hasImage(List<Map<String, Object>> attachments) {
        return presentTypes(attachments).stream().anyMatch(t -> t.equals("photo") || t.equals("image"));
    }

    private static List<String> presentTypes(List<Map<String, Object>> attachments) {
        List<String> types = new ArrayList<>();
        for (Map<String, Object> att : attachments) {
            if (att == null) {
                continue;
            }
            Object type = att.get("type");
            if (type != null && !type.toString().isBlank()) {
                types.add(type.toString().toLowerCase(Locale.ROOT));
            }
        }
        return types;
    }
}
