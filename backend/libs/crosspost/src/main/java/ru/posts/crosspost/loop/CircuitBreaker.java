package ru.posts.crosspost.loop;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ru.posts.crosspost.config.CrosspostConfigSupport;
import ru.posts.crosspost.config.CrosspostProperties;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;

/** L6 automatic circuit breaker — auto_pause on volume or consecutive loop skips. */
public class CircuitBreaker {

    public record Trip(boolean tripped, String reason) {
        public static Trip ok() {
            return new Trip(false, null);
        }

        public static Trip trip(String reason) {
            return new Trip(true, reason);
        }
    }

    private final CrosspostItemRepository itemRepository;
    private final CrosspostRuleRepository ruleRepository;
    private final CrosspostProperties properties;

    public CircuitBreaker(
            CrosspostItemRepository itemRepository,
            CrosspostRuleRepository ruleRepository,
            CrosspostProperties properties) {
        this.itemRepository = itemRepository;
        this.ruleRepository = ruleRepository;
        this.properties = properties;
    }

    public Trip evaluateAndMaybePause(CrosspostRuleEntity rule, CrosspostItemEntity latest) {
        Map<String, Object> config = rule.getConfig();
        int maxItems = CrosspostConfigSupport.circuitMaxItems(config, properties.getCircuitMaxItems());
        int maxLoops = CrosspostConfigSupport.circuitMaxLoopSkips(config, properties.getCircuitMaxLoopSkips());
        Instant windowStart = Instant.now().minus(properties.getCircuitWindow());
        long recent = itemRepository.countByRuleIdAndCreatedAtAfter(rule.getId(), windowStart);
        if (recent > maxItems) {
            pause(rule, "circuit_volume");
            return Trip.trip("circuit_volume");
        }
        if (latest != null && "skipped_loop".equals(latest.getStatus())) {
            int consecutive = latest.getConsecutiveLoopSkips();
            if (consecutive >= maxLoops) {
                pause(rule, "circuit_loop_skips");
                return Trip.trip("circuit_loop_skips");
            }
        }
        return Trip.ok();
    }

    private void pause(CrosspostRuleEntity rule, String reason) {
        if ("auto_paused".equals(rule.getStatus())) {
            return;
        }
        rule.setStatus("auto_paused");
        rule.setUpdatedAt(Instant.now());
        Map<String, Object> config =
                new java.util.LinkedHashMap<>(rule.getConfig() == null ? Map.of() : rule.getConfig());
        config.put("auto_pause_reason", reason);
        config.put("auto_paused_at", Instant.now().toString());
        rule.setConfig(config);
        ruleRepository.save(rule);
    }

    public static int nextConsecutiveLoopSkips(CrosspostItemEntity previous, String newStatus) {
        if (!"skipped_loop".equals(newStatus)) {
            return 0;
        }
        if (previous == null) {
            return 1;
        }
        return previous.getConsecutiveLoopSkips() + 1;
    }

    public UUID ruleId(CrosspostRuleEntity rule) {
        return rule.getId();
    }
}
