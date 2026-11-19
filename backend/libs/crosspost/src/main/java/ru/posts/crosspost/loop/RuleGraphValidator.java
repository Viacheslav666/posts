package ru.posts.crosspost.loop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L5: detect cycles in the crosspost rule graph. Saving is still allowed; callers surface
 * {@code cycle_detected} as a warning.
 */
public final class RuleGraphValidator {

    private RuleGraphValidator() {}

    public record Edge(UUID ruleId, UUID sourceChannelId, UUID targetChannelId) {}

    public record Warning(String code, String message, List<UUID> channelPath) {}

    public static List<Warning> validate(List<Edge> edges) {
        Map<UUID, List<UUID>> adj = new HashMap<>();
        for (Edge e : edges) {
            if (e.sourceChannelId().equals(e.targetChannelId())) {
                continue; // source=target rejected elsewhere
            }
            adj.computeIfAbsent(e.sourceChannelId(), k -> new ArrayList<>()).add(e.targetChannelId());
        }
        List<Warning> warnings = new ArrayList<>();
        Set<UUID> visitedGlobal = new HashSet<>();
        for (UUID start : adj.keySet()) {
            if (visitedGlobal.contains(start)) {
                continue;
            }
            ArrayDeque<UUID> stack = new ArrayDeque<>();
            Set<UUID> onStack = new HashSet<>();
            dfs(start, adj, stack, onStack, visitedGlobal, warnings);
        }
        return warnings;
    }

    private static void dfs(
            UUID node,
            Map<UUID, List<UUID>> adj,
            ArrayDeque<UUID> stack,
            Set<UUID> onStack,
            Set<UUID> visitedGlobal,
            List<Warning> warnings) {
        stack.addLast(node);
        onStack.add(node);
        visitedGlobal.add(node);
        for (UUID next : adj.getOrDefault(node, List.of())) {
            if (onStack.contains(next)) {
                List<UUID> path = new ArrayList<>();
                boolean take = false;
                for (UUID n : stack) {
                    if (n.equals(next)) {
                        take = true;
                    }
                    if (take) {
                        path.add(n);
                    }
                }
                path.add(next);
                warnings.add(new Warning(
                        "cycle_detected",
                        "Crosspost rule graph contains a cycle; L1–L4 still prevent loops",
                        List.copyOf(path)));
                continue;
            }
            if (!visitedGlobal.contains(next)) {
                dfs(next, adj, stack, onStack, visitedGlobal, warnings);
            }
        }
        stack.removeLast();
        onStack.remove(node);
    }
}
