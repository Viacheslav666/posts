package ru.posts.antispam.model;

import java.util.UUID;
import ru.posts.antispam.engine.AhoCorasickMatcher;

/** Rule snapshot compiled for fast evaluation (cached by config_version). */
public record CompiledRule(
        UUID ruleId,
        String name,
        int priority,
        int configVersion,
        boolean dryRun,
        java.util.Set<String> sections,
        RuleConfig config,
        AhoCorasickMatcher blackMatcher,
        AhoCorasickMatcher whiteMatcher) {}
