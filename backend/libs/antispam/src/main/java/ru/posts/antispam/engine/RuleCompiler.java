package ru.posts.antispam.engine;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ru.posts.antispam.model.CompiledRule;
import ru.posts.antispam.model.RuleConfig;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.AntispamWordListEntity;

/** Compiles DB rules + word lists into cached matchers. */
public final class RuleCompiler {

    private RuleCompiler() {}

    public static CompiledRule compile(AntispamRuleEntity rule, Collection<AntispamWordListEntity> wordLists) {
        RuleConfig config = RuleConfig.fromMap(rule.getConfig());
        Map<UUID, AntispamWordListEntity> byId = wordLists == null
                ? Map.of()
                : wordLists.stream().collect(Collectors.toMap(AntispamWordListEntity::getId, w -> w, (a, b) -> a));

        LinkedHashSet<String> black = new LinkedHashSet<>(config.blackWords());
        LinkedHashSet<String> white = new LinkedHashSet<>(config.whiteWords());
        for (UUID id : config.blackListIds()) {
            AntispamWordListEntity list = byId.get(id);
            if (list != null && "black".equals(list.getMode()) && list.getWords() != null) {
                black.addAll(list.getWords());
            }
        }
        for (UUID id : config.whiteListIds()) {
            AntispamWordListEntity list = byId.get(id);
            if (list != null && "white".equals(list.getMode()) && list.getWords() != null) {
                white.addAll(list.getWords());
            }
        }

        Set<String> sections = rule.getSections() == null
                ? Set.of()
                : rule.getSections().stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());

        return new CompiledRule(
                rule.getId(),
                rule.getName(),
                rule.getPriority(),
                rule.getConfigVersion(),
                rule.isDryRun(),
                sections,
                config,
                AhoCorasickMatcher.compile(black),
                AhoCorasickMatcher.compile(white));
    }

    public static List<CompiledRule> compileAll(
            List<AntispamRuleEntity> rules, Collection<AntispamWordListEntity> wordLists) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream().map(r -> compile(r, wordLists)).toList();
    }
}
