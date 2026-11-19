package ru.posts.antispam.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AntispamLogReconcilerTest {

    @Test
    void pendingAndRestoredMoveWhenActionsSettle() {
        assertThat(AntispamLogReconciler.nextState("pending", List.of("succeeded", "succeeded")))
                .isEqualTo("done");
        assertThat(AntispamLogReconciler.nextState("pending", List.of("succeeded", "pending")))
                .isNull();
        assertThat(AntispamLogReconciler.nextState("pending", List.of("failed_permanent")))
                .isEqualTo("failed");
        assertThat(AntispamLogReconciler.nextState("pending", List.of("cancelled")))
                .isEqualTo("failed");
        assertThat(AntispamLogReconciler.nextState("restored", List.of("failed_permanent")))
                .isEqualTo("restore_failed");
        assertThat(AntispamLogReconciler.nextState("restored", List.of("succeeded")))
                .isNull();
    }
}
