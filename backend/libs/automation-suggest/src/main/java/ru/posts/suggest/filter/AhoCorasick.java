package ru.posts.suggest.filter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Minimal Aho–Corasick automaton over already-normalized (case/ё) patterns. Matching is
 * case-sensitive on the provided input — callers must normalize text first.
 */
public final class AhoCorasick {

    private final Node root;
    private final List<String> patterns;

    private AhoCorasick(Node root, List<String> patterns) {
        this.root = root;
        this.patterns = patterns;
    }

    public static AhoCorasick build(Collection<String> rawPatterns) {
        List<String> patterns = new ArrayList<>();
        if (rawPatterns != null) {
            for (String raw : rawPatterns) {
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                patterns.add(raw);
            }
        }
        Node root = new Node();
        for (String pattern : patterns) {
            Node node = root;
            for (int i = 0; i < pattern.length(); ) {
                int cp = pattern.codePointAt(i);
                i += Character.charCount(cp);
                node = node.children.computeIfAbsent(cp, ignored -> new Node());
            }
            if (node.outputs.isEmpty()) {
                node.outputs = new ArrayList<>();
            }
            node.outputs.add(pattern);
        }
        buildFailureLinks(root);
        return new AhoCorasick(root, List.copyOf(patterns));
    }

    public List<String> patterns() {
        return patterns;
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    /** Unique patterns that occur at least once in {@code text}. */
    public List<String> findAll(String text) {
        if (text == null || text.isEmpty() || patterns.isEmpty()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Node node = root;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            while (node != root && !node.children.containsKey(cp)) {
                node = node.fail;
            }
            Node child = node.children.get(cp);
            if (child != null) {
                node = child;
            } else {
                node = root;
            }
            if (!node.outputs.isEmpty()) {
                found.addAll(node.outputs);
            }
            // follow output links via fail chain outputs already merged at build time
            i = next;
        }
        return List.copyOf(found);
    }

    public boolean matchesAny(String text) {
        return !findAll(text).isEmpty();
    }

    /**
     * Patterns that match as whole words (Unicode letter/digit/_ boundaries) in {@code text}.
     */
    public List<String> findAllWholeWord(String text) {
        if (text == null || text.isEmpty() || patterns.isEmpty()) {
            return List.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Node node = root;
        // track match end index; start = end - pattern length in code points is awkward with
        // variable-width — re-scan using index of match end
        int[] codePointStarts = new int[text.length() + 1];
        int cpCount = 0;
        for (int i = 0; i < text.length(); ) {
            codePointStarts[cpCount++] = i;
            i += Character.charCount(text.codePointAt(i));
        }
        codePointStarts[cpCount] = text.length();

        int cpIndex = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            while (node != root && !node.children.containsKey(cp)) {
                node = node.fail;
            }
            Node child = node.children.get(cp);
            node = child != null ? child : root;
            if (!node.outputs.isEmpty()) {
                for (String pattern : node.outputs) {
                    int patternCpLen = pattern.codePointCount(0, pattern.length());
                    int startCp = cpIndex - patternCpLen + 1;
                    if (startCp < 0) {
                        continue;
                    }
                    int start = codePointStarts[startCp];
                    int end = next;
                    if (isWholeWord(text, start, end)) {
                        found.add(pattern);
                    }
                }
            }
            i = next;
            cpIndex++;
        }
        return List.copyOf(found);
    }

    public boolean matchesAnyWholeWord(String text) {
        return !findAllWholeWord(text).isEmpty();
    }

    static boolean isWholeWord(String text, int start, int end) {
        if (start > 0 && isWordChar(text.codePointBefore(start))) {
            return false;
        }
        if (end < text.length() && isWordChar(text.codePointAt(end))) {
            return false;
        }
        return true;
    }

    static boolean isWordChar(int cp) {
        return Character.isLetterOrDigit(cp) || cp == '_';
    }

    private static void buildFailureLinks(Node root) {
        Queue<Node> queue = new ArrayDeque<>();
        root.fail = root;
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            for (Map.Entry<Integer, Node> entry : current.children.entrySet()) {
                int cp = entry.getKey();
                Node child = entry.getValue();
                Node fail = current.fail;
                while (fail != root && !fail.children.containsKey(cp)) {
                    fail = fail.fail;
                }
                Node failChild = fail.children.get(cp);
                child.fail = failChild != null && failChild != child ? failChild : root;
                if (child.fail.outputs.isEmpty()) {
                    // keep own outputs
                } else if (child.outputs.isEmpty()) {
                    child.outputs = child.fail.outputs;
                } else {
                    List<String> merged = new ArrayList<>(child.outputs);
                    for (String out : child.fail.outputs) {
                        if (!merged.contains(out)) {
                            merged.add(out);
                        }
                    }
                    child.outputs = merged;
                }
                queue.add(child);
            }
        }
    }

    private static final class Node {
        private final Map<Integer, Node> children = new HashMap<>();
        private Node fail;
        private List<String> outputs = Collections.emptyList();
    }
}
