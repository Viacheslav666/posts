package ru.posts.publisher.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RemoteActionPrerequisiteTest {

    @Test
    void deleteWaitsWhileReplyIsInFlightAndProceedsAfterATerminalFailure() {
        assertThat(RemoteActionProcessor.mustWait(null)).isTrue();
        assertThat(RemoteActionProcessor.mustWait("pending")).isTrue();
        assertThat(RemoteActionProcessor.mustWait("queued")).isTrue();
        assertThat(RemoteActionProcessor.mustWait("in_progress")).isTrue();
        assertThat(RemoteActionProcessor.mustWait("failed_retryable")).isTrue();
        assertThat(RemoteActionProcessor.mustWait("succeeded")).isFalse();
        assertThat(RemoteActionProcessor.mustWait("failed_permanent")).isFalse();
        assertThat(RemoteActionProcessor.mustWait("cancelled")).isFalse();
    }
}
