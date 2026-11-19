package ru.posts.suggest.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ru.posts.suggest.config.SuggestConfigSupport;

/**
 * Compiles include/exclude/stop keyword lists into Aho–Corasick automata and caches them by {@code
 * config_version + keyword hash}.
 */
public final class KeywordAutomatonCache {

    private final ConcurrentHashMap<String, CompiledKeywords> cache = new ConcurrentHashMap<>();

    public CompiledKeywords get(Map<String, Object> config) {
        Map<String, Object> cfg = SuggestConfigSupport.withDefaults(config);
        Map<String, Object> filters = SuggestConfigSupport.filters(cfg);
        int version = SuggestConfigSupport.configVersion(cfg);
        List<String> include = normalizeKeywords(SuggestConfigSupport.stringList(filters.get("keyword_include")));
        List<String> exclude = normalizeKeywords(SuggestConfigSupport.stringList(filters.get("keyword_exclude")));
        List<String> stop = normalizeKeywords(SuggestConfigSupport.stringList(filters.get("stop_words")));
        String mode = SuggestConfigSupport.keywordMatchMode(cfg);
        String key = cacheKey(version, include, exclude, stop, mode);
        return cache.computeIfAbsent(
                key,
                ignored -> new CompiledKeywords(
                        AhoCorasick.build(include), AhoCorasick.build(exclude), AhoCorasick.build(stop), mode));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public static List<String> normalizeKeywords(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            out.add(SuggestFilterEngine.normalizeText(item));
        }
        return List.copyOf(out);
    }

    private static String cacheKey(
            int version, List<String> include, List<String> exclude, List<String> stop, String mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(version).append('|').append(mode).append('|');
        appendJoined(sb, include);
        sb.append('|');
        appendJoined(sb, exclude);
        sb.append('|');
        appendJoined(sb, stop);
        return version + ":" + sha256(sb.toString());
    }

    private static void appendJoined(StringBuilder sb, List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append('\u0001');
            }
            sb.append(items.get(i));
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public record CompiledKeywords(AhoCorasick include, AhoCorasick exclude, AhoCorasick stop, String matchMode) {

        public CompiledKeywords {
            include = include == null ? AhoCorasick.build(List.of()) : include;
            exclude = exclude == null ? AhoCorasick.build(List.of()) : exclude;
            stop = stop == null ? AhoCorasick.build(List.of()) : stop;
            matchMode = matchMode == null || matchMode.isBlank()
                    ? SuggestConfigSupport.KEYWORD_MATCH_SUBSTRING
                    : matchMode.toLowerCase(Locale.ROOT);
        }

        public boolean wholeWord() {
            return SuggestConfigSupport.KEYWORD_MATCH_WHOLE_WORD.equals(matchMode);
        }
    }
}
