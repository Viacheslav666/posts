package ru.posts.api.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.idempotency")
public class IdempotencyProperties {

    /** How long stored Idempotency-Key responses remain valid (spec: 24h). */
    private Duration ttl = Duration.ofHours(24);

    /**
     * How long to wait for an in-flight sibling request with the same key before returning 409
     * {@code idempotency_conflict}.
     */
    private Duration inFlightWait = Duration.ofSeconds(5);

    private Duration inFlightPoll = Duration.ofMillis(50);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getInFlightWait() {
        return inFlightWait;
    }

    public void setInFlightWait(Duration inFlightWait) {
        this.inFlightWait = inFlightWait;
    }

    public Duration getInFlightPoll() {
        return inFlightPoll;
    }

    public void setInFlightPoll(Duration inFlightPoll) {
        this.inFlightPoll = inFlightPoll;
    }
}
