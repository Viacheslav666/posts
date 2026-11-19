package ru.posts.suggest.filter;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import ru.posts.suggest.config.SuggestConfigSupport;
import ru.posts.suggest.filter.KeywordAutomatonCache.CompiledKeywords;

/**
 * Pure suggest filter function (BE-34). Mode ({@code auto}/{@code manual}/{@code hybrid}) is applied
 * by the caller — this engine returns only {@link SuggestAction#PUBLISH}, {@link
 * SuggestAction#REJECT}, or {@link SuggestAction#FILTER_OUT}.
 *
 * <p>Hard rejects ({@code REJECT}): blacklist, closed profile, messages required, stop words,
 * keyword_exclude. Soft fails ({@code FILTER_OUT}): missing keyword_include, limits, dedup,
 * schedule, ads pause, attachment/text skips.
 */
public final class SuggestFilterEngine {

    private static final Pattern URL =
            Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>\\[\\]()\"']+|vk\\.cc/\\S+|youla\\.ru/\\S+)");

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("H:mm");

    private final KeywordAutomatonCache cache;

    public SuggestFilterEngine() {
        this(new KeywordAutomatonCache());
    }

    public SuggestFilterEngine(KeywordAutomatonCache cache) {
        this.cache = cache == null ? new KeywordAutomatonCache() : cache;
    }

    /**
     * Instance entry-point that reuses the injected {@link KeywordAutomatonCache}. (Java cannot
     * overload a static and instance method with the same erased signature.)
     */
    public SuggestDecision evaluateWithCache(
            SuggestFilterItem item, Map<String, Object> config, SuggestFilterContext ctx) {
        return evaluate(item, config, ctx, cache);
    }

    public KeywordAutomatonCache cache() {
        return cache;
    }

    public static SuggestDecision evaluate(
            SuggestFilterItem item, Map<String, Object> config, SuggestFilterContext ctx) {
        return evaluate(item, config, ctx, null);
    }

    public static SuggestDecision evaluate(
            SuggestFilterItem item,
            Map<String, Object> config,
            SuggestFilterContext ctx,
            KeywordAutomatonCache automatonCache) {
        Map<String, Object> cfg = SuggestConfigSupport.withDefaults(config);
        SuggestFilterContext context = ctx == null ? SuggestFilterContext.empty(Instant.now()) : ctx;
        SuggestFilterItem safeItem = item == null ? SuggestFilterItem.of("", null, List.of()) : item;

        Map<String, Object> filters = SuggestConfigSupport.filters(cfg);
        String authorId =
                safeItem.authorVkId() == null ? "" : safeItem.authorVkId().trim();

        // 1. Whitelist → PUBLISH, skip other filters
        if (!authorId.isEmpty() && containsAuthor(filters.get("author_whitelist"), authorId)) {
            return SuggestDecision.publish(
                    reason("author_whitelist", "Автор в белом списке — публикация без остальных фильтров"));
        }

        // 2. Blacklist → REJECT
        if (!authorId.isEmpty() && containsAuthor(filters.get("author_blacklist"), authorId)) {
            return SuggestDecision.reject(reason("author_blacklist", "Автор в чёрном списке"));
        }

        // 3. Closed profile
        if (SuggestConfigSupport.bool(filters.get("reject_closed_profile"), false)
                && Boolean.TRUE.equals(context.authorProfileClosed())) {
            return SuggestDecision.reject(reason("closed_profile", "Закрытый профиль автора"));
        }

        // 4. Messages not allowed
        if (SuggestConfigSupport.bool(filters.get("require_messages_allowed"), false)
                && Boolean.FALSE.equals(context.authorMessagesAllowed())) {
            return SuggestDecision.reject(reason("no_dialog_with_community", "Нет диалога с сообществом"));
        }

        String text = safeItem.text();
        String normalized = normalizeText(text);
        List<Map<String, Object>> attachments = safeItem.attachments();
        Set<String> presentTypes = presentAttachmentTypes(attachments);

        List<SuggestReason> soft = new ArrayList<>();

        // 5. skip_if_no_text / skip_if_no_image / skip_if_has_attachment_types / exclude_links
        if (SuggestConfigSupport.bool(filters.get("skip_if_no_text"), false) && isBlankText(text)) {
            soft.add(reason("skip_if_no_text", "Нет текста в предложении"));
        }
        if (SuggestConfigSupport.bool(filters.get("skip_if_no_image"), false)
                && !containsAnyType(presentTypes, List.of("photo"))) {
            soft.add(reason("skip_if_no_image", "Нет изображения в предложении"));
        }
        List<String> bannedTypes = SuggestConfigSupport.stringList(filters.get("skip_if_has_attachment_types"));
        if (!bannedTypes.isEmpty() && containsAnyType(presentTypes, bannedTypes)) {
            soft.add(reason("skip_if_has_attachment_types", "Запрещённый тип вложения"));
        }
        if (SuggestConfigSupport.bool(filters.get("exclude_links"), false)
                && (containsLink(text) || containsAnyType(presentTypes, List.of("link")))) {
            soft.add(reason("exclude_links", "Ссылки запрещены"));
        }

        // 6. Keywords
        CompiledKeywords compiled = automatonCache != null ? automatonCache.get(cfg) : compileOnce(cfg);
        boolean wholeWord = compiled.wholeWord();

        if (!compiled.include().isEmpty()) {
            List<String> hits = wholeWord
                    ? compiled.include().findAllWholeWord(normalized)
                    : compiled.include().findAll(normalized);
            if (hits.isEmpty()) {
                soft.add(reason("keyword_include", "Не найдено обязательное ключевое слово"));
            }
        }
        if (!compiled.exclude().isEmpty()) {
            List<String> hits = wholeWord
                    ? compiled.exclude().findAllWholeWord(normalized)
                    : compiled.exclude().findAll(normalized);
            if (!hits.isEmpty()) {
                return SuggestDecision.reject(
                        reason("keyword_exclude", "Найдено запрещённое ключевое слово: " + hits.getFirst()));
            }
        }
        if (!compiled.stop().isEmpty()) {
            List<String> hits = wholeWord
                    ? compiled.stop().findAllWholeWord(normalized)
                    : compiled.stop().findAll(normalized);
            if (!hits.isEmpty()) {
                return SuggestDecision.reject(reason("stop_word", "Найдено стоп-слово: " + hits.getFirst()));
            }
        }

        // 7. Limits
        Map<String, Object> limits = SuggestConfigSupport.limits(cfg);
        addLimitIfExceeded(
                soft,
                limits,
                "author_per_hour",
                context.authorPublishedLastHour(),
                "author_limit_hour",
                "Превышен лимит публикаций автора за час");
        addLimitIfExceeded(
                soft,
                limits,
                "author_per_day",
                context.authorPublishedLastDay(),
                "author_limit_day",
                "Превышен лимит публикаций автора за сутки");
        addLimitIfExceeded(
                soft,
                limits,
                "author_per_week",
                context.authorPublishedLastWeek(),
                "author_limit_week",
                "Превышен лимит публикаций автора за неделю");
        addLimitIfExceeded(
                soft,
                limits,
                "channel_per_hour",
                context.publishedLastHour(),
                "channel_limit_hour",
                "Превышен лимит публикаций канала за час");
        addLimitIfExceeded(
                soft,
                limits,
                "channel_per_day",
                context.publishedLastDay(),
                "channel_limit_day",
                "Превышен лимит публикаций канала за сутки");
        addLimitIfExceeded(
                soft,
                limits,
                "channel_per_week",
                context.publishedLastWeek(),
                "channel_limit_week",
                "Превышен лимит публикаций канала за неделю");

        // 8. Near duplicate
        if (SuggestConfigSupport.dedupEnabled(cfg) && context.nearDuplicate()) {
            soft.add(reason("near_duplicate", "Похожая запись уже публиковалась"));
        }

        // 9. Schedule windows
        if (!isWithinSchedule(cfg, context)) {
            soft.add(reason("outside_schedule", "Вне окна расписания"));
        }

        // 10. Ads pause (scheduler sets adsPauseActive when min wait not elapsed)
        if (context.adsPauseActive()) {
            soft.add(reason("pause_after_ads", "Пауза после рекламного поста"));
        }

        if (!soft.isEmpty()) {
            return SuggestDecision.filterOut(soft);
        }
        return SuggestDecision.publish();
    }

    /** Lowercase {@link Locale#ROOT} and ё→е. */
    public static String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.replace('ё', 'е').replace('Ё', 'е');
    }

    private static CompiledKeywords compileOnce(Map<String, Object> cfg) {
        Map<String, Object> filters = SuggestConfigSupport.filters(cfg);
        return new CompiledKeywords(
                AhoCorasick.build(KeywordAutomatonCache.normalizeKeywords(
                        SuggestConfigSupport.stringList(filters.get("keyword_include")))),
                AhoCorasick.build(KeywordAutomatonCache.normalizeKeywords(
                        SuggestConfigSupport.stringList(filters.get("keyword_exclude")))),
                AhoCorasick.build(KeywordAutomatonCache.normalizeKeywords(
                        SuggestConfigSupport.stringList(filters.get("stop_words")))),
                SuggestConfigSupport.keywordMatchMode(cfg));
    }

    private static void addLimitIfExceeded(
            List<SuggestReason> soft,
            Map<String, Object> limits,
            String key,
            int published,
            String code,
            String message) {
        int limit = SuggestConfigSupport.intVal(limits.get(key), 0);
        if (limit > 0 && published >= limit) {
            soft.add(reason(code, message));
        }
    }

    static boolean isWithinSchedule(Map<String, Object> config, SuggestFilterContext ctx) {
        Map<String, Object> schedule = SuggestConfigSupport.schedule(config);
        if (!SuggestConfigSupport.bool(schedule.get("enabled"), false)) {
            return true;
        }
        ZoneId zone = ctx.zone() != null ? ctx.zone() : zoneId(SuggestConfigSupport.scheduleTimezone(config));
        Instant now = ctx.now() == null ? Instant.now() : ctx.now();
        ZonedDateTime zdt = now.atZone(zone);
        int isoDay = zdt.getDayOfWeek().getValue();
        LocalTime time = zdt.toLocalTime();

        Object windowsRaw = schedule.get("windows");
        if (!(windowsRaw instanceof List<?> windows) || windows.isEmpty()) {
            // enabled but no windows → treat as always closed
            return false;
        }
        for (Object raw : windows) {
            if (!(raw instanceof Map<?, ?> windowMap)) {
                continue;
            }
            Map<String, Object> window = new LinkedHashMap<>();
            windowMap.forEach((k, v) -> window.put(String.valueOf(k), v));
            Set<Integer> days = dayNumbers(window.get("days"));
            if (days.isEmpty()) {
                days = dayNumbers(window.get("day"));
            }
            if (!days.isEmpty() && !days.contains(isoDay)) {
                continue;
            }
            boolean toEndOfDay = isEndOfDay(window.get("to"));
            LocalTime from = parseTime(window.get("from"), LocalTime.MIDNIGHT);
            LocalTime to = toEndOfDay ? LocalTime.MIDNIGHT : parseTime(window.get("to"), LocalTime.MIDNIGHT);
            if (!toEndOfDay && from.equals(to)) {
                return true; // full day when from==to
            }
            if (toEndOfDay) {
                // [from, 24:00)
                if (!time.isBefore(from)) {
                    return true;
                }
            } else if (from.isBefore(to)) {
                // [from, to)
                if (!time.isBefore(from) && time.isBefore(to)) {
                    return true;
                }
            } else {
                // overnight: [from, 24:00) U [00:00, to)
                if (!time.isBefore(from) || time.isBefore(to)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEndOfDay(Object raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.toString().trim();
        return "24:00".equals(s) || "24:00:00".equals(s);
    }

    private static LocalTime parseTime(Object raw, LocalTime defaultValue) {
        if (raw == null || raw.toString().isBlank()) {
            return defaultValue;
        }
        String s = raw.toString().trim();
        try {
            return LocalTime.parse(s, HM);
        } catch (DateTimeParseException ex) {
            try {
                return LocalTime.parse(s);
            } catch (DateTimeParseException ignored) {
                return defaultValue;
            }
        }
    }

    private static Set<Integer> dayNumbers(Object raw) {
        Set<Integer> out = new LinkedHashSet<>();
        if (raw instanceof Number n) {
            int v = n.intValue();
            if (v >= 1 && v <= 7) {
                out.add(v);
            }
            return out;
        }
        if (!(raw instanceof Collection<?> col)) {
            return out;
        }
        for (Object item : col) {
            if (item instanceof Number n) {
                int v = n.intValue();
                if (v >= 1 && v <= 7) {
                    out.add(v);
                }
            } else if (item != null) {
                String s = item.toString().trim().toLowerCase(Locale.ROOT);
                Integer mapped =
                        switch (s) {
                            case "mon", "monday", "пн" -> 1;
                            case "tue", "tuesday", "вт" -> 2;
                            case "wed", "wednesday", "ср" -> 3;
                            case "thu", "thursday", "чт" -> 4;
                            case "fri", "friday", "пт" -> 5;
                            case "sat", "saturday", "сб" -> 6;
                            case "sun", "sunday", "вс" -> 7;
                            default -> {
                                try {
                                    int v = Integer.parseInt(s);
                                    yield (v >= 1 && v <= 7) ? v : null;
                                } catch (NumberFormatException ex) {
                                    yield null;
                                }
                            }
                        };
                if (mapped != null) {
                    out.add(mapped);
                }
            }
        }
        return out;
    }

    private static ZoneId zoneId(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (Exception ex) {
            return ZoneId.of(SuggestConfigSupport.DEFAULT_TIMEZONE);
        }
    }

    private static boolean containsAuthor(Object raw, String authorId) {
        for (String id : SuggestConfigSupport.stringList(raw)) {
            if (authorId.equals(id.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlankText(String text) {
        return text == null || text.isBlank();
    }

    private static boolean containsLink(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("youla.ru") || lower.contains("vk.cc/")) {
            return true;
        }
        return URL.matcher(text).find();
    }

    static Set<String> presentAttachmentTypes(List<Map<String, Object>> attachments) {
        Set<String> out = new LinkedHashSet<>();
        if (attachments == null) {
            return out;
        }
        for (Map<String, Object> att : attachments) {
            if (att == null) {
                continue;
            }
            Object type = att.get("type");
            if (type == null) {
                continue;
            }
            out.add(canonicalizeType(type.toString()));
        }
        return out;
    }

    static boolean containsAnyType(Set<String> present, Collection<String> wanted) {
        if (wanted == null || wanted.isEmpty() || present == null || present.isEmpty()) {
            return false;
        }
        for (String raw : wanted) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String key = canonicalizeType(raw);
            if (present.contains(key)) {
                return true;
            }
            if ("image".equals(key) && present.contains("photo")) {
                return true;
            }
            if ("photo".equals(key) && present.contains("image")) {
                return true;
            }
        }
        return false;
    }

    static String canonicalizeType(String raw) {
        if (raw == null) {
            return "none";
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "image", "images", "photos" -> "photo";
            case "videos" -> "video";
            case "links" -> "link";
            case "docs", "file", "document" -> "doc";
            default -> v;
        };
    }

    private static SuggestReason reason(String code, String message) {
        return new SuggestReason(code, message);
    }
}
