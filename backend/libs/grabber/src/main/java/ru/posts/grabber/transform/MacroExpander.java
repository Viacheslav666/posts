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
 * <p>Supported: {@code [name_of_the_source]}, {@code [id_of_the_source]},
 * {@code [post_id_of_the_source]}, {@code [rand]a|b[/rand]}, {@code [keyText]},
 * {@code [keyTextEntry]}, {@code [keyTextEntryNotFound]}.
 */
public final class MacroExpander {

    private static final Pattern RAND =
            Pattern.compile("\\[rand](.*?)\\[/rand]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
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
            String keyTextEntryNotFound) {
        public static Context of(String sourceName, String sourceId, String remotePostId) {
            return new Context(sourceName, sourceId, remotePostId, "", "", "");
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
        String result = template.replace("[name_of_the_source]", nullToEmpty(safe.sourceName()))
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
                string(map.get("keyTextEntryNotFound"), base.keyTextEntryNotFound()));
    }

    private static String expandRand(String text, IntUnaryOperator randomIndex) {
        Matcher matcher = RAND.matcher(text);
        StringBuffer sb = new StringBuffer();
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

    private static List<String> splitOptions(String body) {
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
