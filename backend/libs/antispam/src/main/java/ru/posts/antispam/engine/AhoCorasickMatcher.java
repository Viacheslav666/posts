package ru.posts.antispam.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compact Aho-Corasick automaton for multi-pattern keyword search. */
public final class AhoCorasickMatcher {

    private final Node root;

    private AhoCorasickMatcher(Node root) {
        this.root = root;
    }

    public static AhoCorasickMatcher compile(Iterable<String> patterns) {
        Node root = new Node();
        int id = 0;
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String pattern = TextNormalizer.normalizeForKeywords(raw);
            if (pattern.isEmpty()) {
                continue;
            }
            Node node = root;
            for (int i = 0; i < pattern.length(); ) {
                int cp = pattern.codePointAt(i);
                i += Character.charCount(cp);
                node = node.children.computeIfAbsent(cp, k -> new Node());
            }
            if (node.outputs.isEmpty()) {
                node.outputs = new ArrayList<>();
            }
            node.outputs.add(new Match(id++, raw, pattern));
        }
        buildFailureLinks(root);
        return new AhoCorasickMatcher(root);
    }

    public List<Match> findAll(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String normalized = TextNormalizer.normalizeForKeywords(text);
        List<Match> found = new ArrayList<>();
        Node node = root;
        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            i += Character.charCount(cp);
            while (node != root && !node.children.containsKey(cp)) {
                node = node.fail;
            }
            node = node.children.getOrDefault(cp, root);
            if (!node.outputs.isEmpty()) {
                found.addAll(node.outputs);
            }
        }
        return found;
    }

    public boolean containsAny(String text) {
        return !findAll(text).isEmpty();
    }

    private static void buildFailureLinks(Node root) {
        ArrayDeque<Node> queue = new ArrayDeque<>();
        root.fail = root;
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node current = queue.removeFirst();
            for (Map.Entry<Integer, Node> entry : current.children.entrySet()) {
                int cp = entry.getKey();
                Node child = entry.getValue();
                Node fail = current.fail;
                while (fail != root && !fail.children.containsKey(cp)) {
                    fail = fail.fail;
                }
                child.fail = fail.children.getOrDefault(cp, root);
                if (child.fail == child) {
                    child.fail = root;
                }
                if (!child.fail.outputs.isEmpty()) {
                    if (child.outputs.isEmpty()) {
                        child.outputs = new ArrayList<>();
                    }
                    child.outputs.addAll(child.fail.outputs);
                }
                queue.add(child);
            }
        }
    }

    public record Match(int id, String original, String normalized) {}

    private static final class Node {
        private final Map<Integer, Node> children = new HashMap<>();
        private Node fail;
        private List<Match> outputs = Collections.emptyList();
    }
}
