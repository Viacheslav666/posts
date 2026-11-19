package ru.posts.crosspost.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "posts.crosspost")
public class CrosspostProperties {

    /** L2 race window: defer POST_NEW processing so registry commit can land. */
    private Duration deferSeconds = Duration.ofSeconds(15);

    /** L6: max items per rule in rolling window before auto_pause. */
    private int circuitMaxItems = 60;

    private Duration circuitWindow = Duration.ofMinutes(10);

    /** L6: consecutive skipped_loop before auto_pause. */
    private int circuitMaxLoopSkips = 5;

    /** L4 Hamming distance threshold for SimHash near-duplicates. */
    private int dedupHammingDistance = 3;

    private Duration dedupWindow = Duration.ofHours(24);

    /** Default delay before publishing copies. */
    private int defaultDelayMinutes = 0;

    public Duration getDeferSeconds() {
        return deferSeconds;
    }

    public void setDeferSeconds(Duration deferSeconds) {
        this.deferSeconds = deferSeconds;
    }

    public int getCircuitMaxItems() {
        return circuitMaxItems;
    }

    public void setCircuitMaxItems(int circuitMaxItems) {
        this.circuitMaxItems = circuitMaxItems;
    }

    public Duration getCircuitWindow() {
        return circuitWindow;
    }

    public void setCircuitWindow(Duration circuitWindow) {
        this.circuitWindow = circuitWindow;
    }

    public int getCircuitMaxLoopSkips() {
        return circuitMaxLoopSkips;
    }

    public void setCircuitMaxLoopSkips(int circuitMaxLoopSkips) {
        this.circuitMaxLoopSkips = circuitMaxLoopSkips;
    }

    public int getDedupHammingDistance() {
        return dedupHammingDistance;
    }

    public void setDedupHammingDistance(int dedupHammingDistance) {
        this.dedupHammingDistance = dedupHammingDistance;
    }

    public Duration getDedupWindow() {
        return dedupWindow;
    }

    public void setDedupWindow(Duration dedupWindow) {
        this.dedupWindow = dedupWindow;
    }

    public int getDefaultDelayMinutes() {
        return defaultDelayMinutes;
    }

    public void setDefaultDelayMinutes(int defaultDelayMinutes) {
        this.defaultDelayMinutes = defaultDelayMinutes;
    }
}
