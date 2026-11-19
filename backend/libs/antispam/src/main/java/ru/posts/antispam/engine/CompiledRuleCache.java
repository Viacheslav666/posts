package ru.posts.antispam.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ru.posts.antispam.model.CompiledRule;
import ru.posts.persistence.entity.AntispamRuleEntity;
import ru.posts.persistence.entity.AntispamWordListEntity;

/** Caffeine cache of compiled rules keyed by channel + config versions. */
public class CompiledRuleCache {

    private final Cache<String, List<CompiledRule>> cache;

    public CompiledRuleCache() {
        this(10_000, Duration.ofMinutes(30));
    }

    public CompiledRuleCache(long maxSize, Duration ttl) {
        this.cache = Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).build();
    }

    public List<CompiledRule> getOrCompile(
            UUID channelId,
            List<AntispamRuleEntity> rules,
            Collection<AntispamWordListEntity> wordLists) {
        String key = cacheKey(channelId, rules, wordLists);
        return cache.get(key, k -> RuleCompiler.compileAll(rules, wordLists));
    }

    public void invalidateChannel(UUID channelId) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(channelId + "|"));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    static String cacheKey(
            UUID channelId, List<AntispamRuleEntity> rules, Collection<AntispamWordListEntity> wordLists) {
        StringBuilder sb = new StringBuilder();
        sb.append(channelId).append('|');
        if (rules != null) {
            for (AntispamRuleEntity r : rules) {
                sb.append(r.getId())
                        .append(':')
                        .append(r.getConfigVersion())
                        .append(':')
                        .append(r.getPriority())
                        .append(':')
                        .append(r.isDryRun())
                        .append(';');
            }
        }
        sb.append('|');
        if (wordLists != null) {
            for (AntispamWordListEntity w : wordLists) {
                sb.append(w.getId()).append(':').append(w.getVersion()).append(';');
            }
        }
        return sb.toString();
    }
}
