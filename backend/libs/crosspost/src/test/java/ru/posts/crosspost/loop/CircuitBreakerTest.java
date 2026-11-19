package ru.posts.crosspost.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.posts.crosspost.config.CrosspostProperties;
import ru.posts.persistence.entity.CrosspostItemEntity;
import ru.posts.persistence.entity.CrosspostRuleEntity;
import ru.posts.persistence.repository.CrosspostItemRepository;
import ru.posts.persistence.repository.CrosspostRuleRepository;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerTest {

    @Mock
    CrosspostItemRepository itemRepository;

    @Mock
    CrosspostRuleRepository ruleRepository;

    CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new CircuitBreaker(itemRepository, ruleRepository, new CrosspostProperties());
    }

    @Test
    void tripsOnVolume() {
        CrosspostRuleEntity rule = rule();
        when(itemRepository.countByRuleIdAndCreatedAtAfter(eq(rule.getId()), any(Instant.class))).thenReturn(100L);
        var trip = breaker.evaluateAndMaybePause(rule, null);
        assertThat(trip.tripped()).isTrue();
        ArgumentCaptor<CrosspostRuleEntity> cap = ArgumentCaptor.forClass(CrosspostRuleEntity.class);
        verify(ruleRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("auto_paused");
    }

    @Test
    void tripsOnConsecutiveLoopSkips() {
        CrosspostRuleEntity rule = rule();
        when(itemRepository.countByRuleIdAndCreatedAtAfter(eq(rule.getId()), any(Instant.class))).thenReturn(1L);
        CrosspostItemEntity latest = new CrosspostItemEntity();
        latest.setStatus("skipped_loop");
        latest.setConsecutiveLoopSkips(5);
        var trip = breaker.evaluateAndMaybePause(rule, latest);
        assertThat(trip.tripped()).isTrue();
        assertThat(trip.reason()).isEqualTo("circuit_loop_skips");
    }

    @Test
    void consecutiveCounter() {
        assertThat(CircuitBreaker.nextConsecutiveLoopSkips(null, "skipped_loop")).isEqualTo(1);
        CrosspostItemEntity prev = new CrosspostItemEntity();
        prev.setConsecutiveLoopSkips(2);
        assertThat(CircuitBreaker.nextConsecutiveLoopSkips(prev, "skipped_loop")).isEqualTo(3);
        assertThat(CircuitBreaker.nextConsecutiveLoopSkips(prev, "queued")).isEqualTo(0);
    }

    private static CrosspostRuleEntity rule() {
        CrosspostRuleEntity rule = new CrosspostRuleEntity();
        rule.setId(UUID.randomUUID());
        rule.setStatus("active");
        rule.setConfig(Map.of("circuit_max_items", 60, "circuit_max_loop_skips", 5));
        return rule;
    }
}
