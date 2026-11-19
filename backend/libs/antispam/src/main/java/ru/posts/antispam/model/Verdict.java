package ru.posts.antispam.model;

import java.util.List;
import java.util.UUID;

/** Result of {@link ru.posts.antispam.engine.AntispamEngine#evaluate}. */
public record Verdict(boolean matched, UUID ruleId, String ruleName, boolean dryRun, List<String> reasons, List<AntispamAction> actions) {

    public static Verdict none() {
        return new Verdict(false, null, null, false, List.of(), List.of());
    }

    public static Verdict hit(
            UUID ruleId, String ruleName, boolean dryRun, List<String> reasons, List<AntispamAction> actions) {
        return new Verdict(true, ruleId, ruleName, dryRun, List.copyOf(reasons), List.copyOf(actions));
    }
}
