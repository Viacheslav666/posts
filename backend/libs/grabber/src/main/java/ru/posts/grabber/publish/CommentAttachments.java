package ru.posts.grabber.publish;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.posts.grabber.transform.MacroExpander;

/** Parses VK comment attachments: {@code photo-1_2} / {@code video-1_2}, including {@code [rand]}. */
public final class CommentAttachments {

    /** photo-123_456, photo123_456, video-1_2. */
    public static final Pattern TOKEN = Pattern.compile("(?i)^(photo|video)-?\\d+_\\d+$");

    private static final Pattern RAND_BODY =
            Pattern.compile("\\[rand](.*?)\\[/rand]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final int MAX_ATTACHMENTS = 10;

    private CommentAttachments() {}

    public record ParseResult(List<String> valid, List<String> invalid) {}

    public static ParseResult parse(String template, Map<String, Object> config, MacroExpander.Context ctx) {
        String expanded = MacroExpander.expand(template == null ? "" : template, config, ctx);
        return split(expanded);
    }

    /** Validates a stored template before expansion (every {@code [rand]} alternative and loose token). */
    public static List<String> invalidTokens(String template) {
        if (template == null || template.isBlank()) {
            return List.of();
        }
        String withoutRand = RAND_BODY.matcher(template).replaceAll(" ");
        List<String> invalid = new ArrayList<>();
        Matcher rand = RAND_BODY.matcher(template);
        while (rand.find()) {
            for (String option : MacroExpander.splitOptions(rand.group(1))) {
                String token = option.trim();
                if (!token.isEmpty() && !TOKEN.matcher(token).matches()) {
                    invalid.add(token);
                }
            }
        }
        invalid.addAll(split(withoutRand).invalid());
        return List.copyOf(invalid);
    }

    private static ParseResult split(String text) {
        if (text == null || text.isBlank()) {
            return new ParseResult(List.of(), List.of());
        }
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String part : text.split("[,\\s]+")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (TOKEN.matcher(token).matches()) {
                if (valid.size() < MAX_ATTACHMENTS) {
                    valid.add(token);
                } else {
                    invalid.add(token);
                }
            } else {
                invalid.add(token);
            }
        }
        return new ParseResult(List.copyOf(valid), List.copyOf(invalid));
    }
}
