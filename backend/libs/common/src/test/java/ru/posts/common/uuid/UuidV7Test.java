package ru.posts.common.uuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void generateProducesVersion7AndRfcVariant() {
        UUID id = UuidV7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(UuidV7.isUuidV7(id)).isTrue();
    }

    @Test
    void generateIsTimeOrdered() {
        UUID earlier = UuidV7.generate(1_700_000_000_000L, new Random(1));
        UUID later = UuidV7.generate(1_700_000_000_100L, new Random(1));
        assertThat(earlier.compareTo(later)).isNegative();
        assertThat(UuidV7.timestampMillis(earlier)).isEqualTo(1_700_000_000_000L);
        assertThat(UuidV7.timestampMillis(later)).isEqualTo(1_700_000_000_100L);
    }

    @Test
    void generateProducesUniqueIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(ids.add(UuidV7.generateFast())).isTrue();
        }
    }

    @Test
    void timestampMillisRejectsNonV7() {
        assertThatThrownBy(() -> UuidV7.timestampMillis(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
