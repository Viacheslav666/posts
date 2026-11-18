package ru.posts.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code [rand]a|b|c[/rand]} spintax.
 *
 * <p>Retries of the same publication must produce the same text: use {@link #deterministicIndex(String)}
 * seeded by a stable publication key (idempotency key / publication id), never attempt number.
 */
public final class SpintaxExpander {

    private static final Pattern RAND =
            Pattern.compile("\\[rand](.*?)\\[/rand]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SpintaxExpander() {}

    public static String expand(String template) {
        return expand(template, bound -> 0);
    }

    public static String expand(String template, IntUnaryOperator randomIndex) {
        if (template == null || template.isEmpty()) {
            return template == null ? "" : template;
        }
        IntUnaryOperator picker = randomIndex == null ? bound -> 0 : randomIndex;
        Matcher matcher = RAND.matcher(template);
        StringBuffer sb = new StringBuffer();
        int occurrence = 0;
        while (matcher.find()) {
            List<String> options = splitOptions(matcher.group(1));
            String chosen;
            if (options.isEmpty()) {
                chosen = "";
            } else {
                int idx;
                if (picker instanceof SeededIndex seeded) {
                    idx = Math.floorMod(seeded.pick(options.size(), occurrence), options.size());
                } else {
                    idx = Math.floorMod(picker.applyAsInt(options.size()), options.size());
                }
                chosen = options.get(idx);
            }
            occurrence++;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(chosen));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Deterministic picker for a stable seed (publication id / idempotency key). */
    public static IntUnaryOperator deterministicIndex(String seed) {
        byte[] digest = sha256(seed == null ? "" : seed);
        return new SeededIndex(digest);
    }

    public static boolean containsSpintax(String template) {
        return template != null && RAND.matcher(template).find();
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

    private static byte[] sha256(String seed) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return seed.getBytes(StandardCharsets.UTF_8);
        }
    }

    private record SeededIndex(byte[] digest) implements IntUnaryOperator {
        int pick(int bound, int occurrence) {
            if (bound <= 0) {
                return 0;
            }
            int offset = Math.floorMod(occurrence * 4, Math.max(1, digest.length - 3));
            int value = ((digest[offset] & 0xff) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            return Math.floorMod(value, bound);
        }

        @Override
        public int applyAsInt(int bound) {
            return pick(bound, 0);
        }
    }
}
