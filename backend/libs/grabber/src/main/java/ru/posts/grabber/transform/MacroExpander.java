package ru.posts.grabber.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands grabber macros in prepend/append/first-comment text.
 *
 * <p>Placeholders: {@code [name_of_the_source]}, {@code [id_of_the_source]}, {@code
 * [post_id_of_the_source]}, {@code [rand]a|b|c[/rand]}.
 *
 * <p>Conditional blocks are matched against the source post text:
 *
 * <ul>
 *   <li>{@code [keyText]word1|word2,replacement[/keyText]} — whole words
 *   <li>{@code [keyTextEntry]needle,replacement[/keyTextEntry]} — substring
 *   <li>{@code [keyTextEntryNotFound]needle,replacement[/keyTextEntryNotFound]} — substring absent
 * </ul>
 *
 * <p>Bare {@code [keyText]} / {@code [keyTextEntry]} / {@code [keyTextEntryNotFound]} tokens still
 * expand from {@code config.macros} so older rules keep working.
 */
public final class MacroExpander {

    private static final Pattern RAND =
            Pattern.compile("\\[rand](.*?)\\[/rand]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KEY_TEXT_BLOCK =
            Pattern.compile("\\[keyText](.*?)\\[/keyText]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KEY_ENTRY_BLOCK =
            Pattern.compile("\\[keyTextEntry](.*?)\\[/keyTextEntry]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KEY_NOT_FOUND_BLOCK = Pattern.compile(
            "\\[keyTextEntryNotFound](.*?)\\[/keyTextEntryNotFound]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern KEY_TEXT = Pattern.compile("\\[keyText]", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_ENTRY = Pattern.compile("\\[keyTextEntry]", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_NOT_FOUND = Pattern.compile("\\[keyTextEntryNotFound]", Pattern.CASE_INSENSITIVE);

    private MacroExpander() {}

    public record Context(
            String sourceName,
            String sourceId,
            String remotePostId,
            String keyText,
            String keyTextEntry,
            String keyTextEntryNotFound,
            String sourceText) {

        public Context(
                String sourceName,
                String sourceId,
                String remotePostId,
                String keyText,
                String keyTextEntry,
                String keyTextEntryNotFound) {
            this(sourceName, sourceId, remotePostId, keyText, keyTextEntry, keyTextEntryNotFound, "");
        }

        public static Context of(String sourceName, String sourceId, String remotePostId) {
            return new Context(sourceName, sourceId, remotePostId, "", "", "", "");
        }

        public static Context of(String sourceName, String sourceId, String remotePostId, String sourceText) {
            return new Context(sourceName, sourceId, remotePostId, "", "", "", sourceText);
        }
    }

    public static String expand(String template, Context ctx) {
        return expand(template, ctx, ThreadLocalRandom.current()::nextInt);
    }

    /** Testable expand with injected RNG: {@code nextInt(bound)} → index in [0, bound). */
    public static String expand(String template, Context ctx, IntUnaryOperator randomIndex) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        Context safe = ctx == null ? Context.of("", "", "") : ctx;
        String source = nullToEmpty(safe.sourceText());
        String result = replaceBlocks(KEY_TEXT_BLOCK, template, source, MatchMode.WHOLE_WORD, false);
        result = replaceBlocks(KEY_ENTRY_BLOCK, result, source, MatchMode.SUBSTRING, false);
        result = replaceBlocks(KEY_NOT_FOUND_BLOCK, result, source, MatchMode.SUBSTRING, true);
        result = result.replace("[name_of_the_source]", nullToEmpty(safe.sourceName()))
                .replace("[id_of_the_source]", nullToEmpty(safe.sourceId()))
                .replace("[post_id_of_the_source]", nullToEmpty(safe.remotePostId()));
        result = KEY_TEXT.matcher(result).replaceAll(Matcher.quoteReplacement(nullToEmpty(safe.keyText())));
        result = KEY_ENTRY.matcher(result).replaceAll(Matcher.quoteReplacement(nullToEmpty(safe.keyTextEntry())));
        result = KEY_NOT_FOUND
                .matcher(result)
                .replaceAll(Matcher.quoteReplacement(nullToEmpty(safe.keyTextEntryNotFound())));
        return expandRand(result, randomIndex);
    }

    public static String expand(String template, Map<String, Object> config, Context ctx) {
        Context enriched = enrichFromConfig(ctx, config);
        return expand(template, enriched);
    }

    private enum MatchMode {
        WHOLE_WORD,
        SUBSTRING
    }

    private static String replaceBlocks(
            Pattern pattern, String text, String source, MatchMode mode, boolean whenMissing) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String body = matcher.group(1);
            String replacement = blockReplacement(body, source, mode, whenMissing);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Body is {@code keywords,replacement}. Keywords are separated by {@code |}. The first comma
     * splits keywords from the text that is inserted when the condition holds.
     */
    static String blockReplacement(String body, String source, MatchMode mode, boolean whenMissing) {
        String raw = body == null ? "" : body;
        int comma = raw.indexOf(',');
        String keywordsPart = comma < 0 ? raw : raw.substring(0, comma);
        String replacement = comma < 0 ? "" : raw.substring(comma + 1);
        List<String> keywords = splitOptions(keywordsPart).stream()
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toList();
        if (keywords.isEmpty()) {
            return "";
        }
        String haystack = source == null ? "" : source;
        boolean found = keywords.stream().anyMatch(k -> matches(haystack, k, mode));
        boolean emit = whenMissing ? !found : found;
        return emit ? replacement : "";
    }

    private static boolean matches(String text, String keyword, MatchMode mode) {
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        if (mode == MatchMode.SUBSTRING) {
            return text.toLowerCase(java.util.Locale.ROOT).contains(keyword.toLowerCase(java.util.Locale.ROOT));
        }
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])" + Pattern.quote(keyword) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(text).find();
    }

    private static Context enrichFromConfig(Context ctx, Map<String, Object> config) {
        Context base = ctx == null ? Context.of("", "", "") : ctx;
        if (config == null) {
            return base;
        }
        Object macros = config.get("macros");
        if (!(macros instanceof Map<?, ?> map)) {
            return base;
        }
        return new Context(
                base.sourceName(),
                base.sourceId(),
                base.remotePostId(),
                string(map.get("keyText"), base.keyText()),
                string(map.get("keyTextEntry"), base.keyTextEntry()),
                string(map.get("keyTextEntryNotFound"), base.keyTextEntryNotFound()),
                base.sourceText());
    }

    private static String expandRand(String text, IntUnaryOperator randomIndex) {
        Matcher matcher = RAND.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String body = matcher.group(1);
            List<String> options = splitOptions(body);
            String chosen = options.isEmpty()
                    ? ""
                    : options.get(Math.floorMod(randomIndex.applyAsInt(options.size()), options.size()));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(chosen));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static List<String> splitOptions(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '|') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String string(Object raw, String fallback) {
        if (raw == null) {
            return fallback == null ? "" : fallback;
        }
        String value = raw.toString();
        return value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }
}
