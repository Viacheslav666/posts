package ru.posts.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PostPublicationRollupTest {

    @Test
    void allFailedPermanentOnlyWhenEveryPublicationEndedThatWay() {
        assertThat(PostPublicationRollup.allFailedPermanent(List.of())).isFalse();
        assertThat(PostPublicationRollup.allFailedPermanent(null)).isFalse();
        assertThat(PostPublicationRollup.allFailedPermanent(List.of("failed_permanent")))
                .isTrue();
        assertThat(PostPublicationRollup.allFailedPermanent(List.of("failed_permanent", "failed_permanent")))
                .isTrue();
        assertThat(PostPublicationRollup.allFailedPermanent(List.of("failed_permanent", "succeeded")))
                .isFalse();
        assertThat(PostPublicationRollup.allFailedPermanent(List.of("failed_permanent", "pending")))
                .isFalse();
        assertThat(PostPublicationRollup.allFailedPermanent(List.of("failed_permanent", "cancelled")))
                .isFalse();
        assertThat(PostPublicationRollup.allFailedPermanent(List.of("failed_retryable")))
                .isFalse();
    }
}
