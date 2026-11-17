package ru.posts.ratelimit.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryLoginRateLimiterTest {

    @Test
    void limitsAfterCapacity() {
        InMemoryLoginRateLimiter limiter = new InMemoryLoginRateLimiter(2, Duration.ofMinutes(1));
        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isTrue();
        assertThat(limiter.tryConsume("a")).isFalse();
        limiter.reset("a");
        assertThat(limiter.tryConsume("a")).isTrue();
    }
}
