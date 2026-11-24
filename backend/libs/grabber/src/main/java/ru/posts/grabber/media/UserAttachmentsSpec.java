package ru.posts.grabber.media;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.posts.content.VkStringAttachmentValidator;

/**
 * "Свои вложения" for grabbed posts that end up without attachments. Syntax:
 *
 * <ul>
 *   <li>{@code photo-1_2, video-1_3} — always attached;
 *   <li>{@code [rand]photo-1_2|photo-1_3,video-1_4[/rand]} — one random variant (each variant is a
 *       comma-separated list);
 *   <li>{@code [keyTextEntry]photo-1_2,photo-1_3|прикол,смеят[/keyTextEntry]} — attached when the
 *       post text contains any of the keywords (case-insensitive substring, so stems match).
 * </ul>
 */
public final class UserAttachmentsSpec {

    public static final int MAX_LENGTH = 4000;

    private static final Pattern BLOCK =
            Pattern.compile("\\[(rand|keyTextEntry)](.*?)\\[/\\1]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SEPARATORS = Pattern.compile("[,;\\s]+");

    private UserAttachmentsSpec() {}

    /** Human-readable (Russian) validation errors; empty when the spec is valid or blank. */
    public static List<String> validate(String spec) {
        List<String> errors = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            return errors;
        }
        if (spec.length() > MAX_LENGTH) {
            errors.add("Поле «Свои вложения» длиннее " + MAX_LENGTH + " символов");
            return errors;
        }
        Matcher m = BLOCK.matcher(spec);
        StringBuilder rest = new StringBuilder();
        int last = 0;
        while (m.find()) {
            rest.append(spec, last, m.start()).append(',');
            last = m.end();
            String tag = m.group(1).toLowerCase(Locale.ROOT);
            String body = m.group(2);
            if (body.contains("[")) {
                errors.add("Вложенные блоки в [" + m.group(1) + "] не поддерживаются");
                continue;
            }
            if ("rand".equals(tag)) {
                String[] variants = body.split("\\|", -1);
                if (variants.length < 2) {
                    errors.add("[rand] должен содержать минимум два варианта через «|»");
                }
                for (String variant : variants) {
                    List<String> refs = splitRefs(variant);
                    if (refs.isEmpty()) {
                        errors.add("Пустой вариант в [rand]");
                    }
                    addInvalid(errors, refs);
                }
            } else {
                int bar = body.lastIndexOf('|');
                if (bar < 0) {
                    errors.add("[keyTextEntry] должен иметь вид вложения|слово1,слово2");
                    continue;
                }
                List<String> refs = splitRefs(body.substring(0, bar));
                if (refs.isEmpty()) {
                    errors.add("В [keyTextEntry] не указаны вложения");
                }
                addInvalid(errors, refs);
                if (keywords(body.substring(bar + 1)).isEmpty()) {
                    errors.add("В [keyTextEntry] не указаны ключевые слова");
                }
            }
        }
        rest.append(spec.substring(last));
        if (rest.indexOf("[") >= 0 || rest.indexOf("]") >= 0) {
            errors.add("Незакрытый или неизвестный блок: поддерживаются только [rand]…[/rand] и "
                    + "[keyTextEntry]…[/keyTextEntry]");
        } else {
            addInvalid(errors, splitRefs(rest.toString()));
        }
        return List.copyOf(new LinkedHashSet<>(errors));
    }

    /**
     * Resolves the spec for one post. Invalid tokens are skipped (the rule was validated on save);
     * duplicates are removed and the result is capped at the VK limit.
     */
    public static List<String> resolve(String spec, String postText, RandomGenerator random) {
        if (spec == null || spec.isBlank()) {
            return List.of();
        }
        String text = postText == null ? "" : postText.toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = BLOCK.matcher(spec);
        int last = 0;
        while (m.find()) {
            // Plain tokens keep the position they were written in, including those before a block.
            addValid(out, splitRefs(spec.substring(last, m.start())));
            last = m.end();
            String tag = m.group(1).toLowerCase(Locale.ROOT);
            String body = m.group(2);
            if ("rand".equals(tag)) {
                String[] variants = body.split("\\|");
                if (variants.length > 0) {
                    addValid(out, splitRefs(variants[random.nextInt(variants.length)]));
                }
            } else {
                int bar = body.lastIndexOf('|');
                if (bar < 0) {
                    continue;
                }
                List<String> words = keywords(body.substring(bar + 1));
                if (words.stream().anyMatch(text::contains)) {
                    addValid(out, splitRefs(body.substring(0, bar)));
                }
            }
        }
        addValid(out, splitRefs(spec.substring(last)));
        return out.stream().limit(GrabbedAttachments.VK_MAX_ATTACHMENTS).toList();
    }

    private static List<String> splitRefs(String raw) {
        List<String> out = new ArrayList<>();
        for (String token : SEPARATORS.split(raw == null ? "" : raw.trim())) {
            if (!token.isBlank()) {
                out.add(token.trim());
            }
        }
        return out;
    }

    private static List<String> keywords(String raw) {
        List<String> out = new ArrayList<>();
        for (String word : raw.split(",")) {
            String w = word.trim().toLowerCase(Locale.ROOT);
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    private static void addInvalid(List<String> errors, List<String> refs) {
        for (String bad : VkStringAttachmentValidator.invalidOf(refs)) {
            errors.add("Некорректное вложение «" + bad + "»: ожидается вид photo-123_456 или video-123_456");
        }
    }

    private static void addValid(Set<String> out, List<String> refs) {
        for (String ref : VkStringAttachmentValidator.normalize(refs)) {
            if (VkStringAttachmentValidator.isValid(ref)) {
                out.add(ref);
            }
        }
    }
}
