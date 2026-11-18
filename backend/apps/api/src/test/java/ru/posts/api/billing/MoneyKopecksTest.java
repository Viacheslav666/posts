package ru.posts.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyKopecksTest {

    @Test
    void roundTrip() {
        assertThat(MoneyKopecks.toKopecks(new BigDecimal("13.00"))).isEqualTo(1300L);
        assertThat(MoneyKopecks.toRubles(2700L)).isEqualByComparingTo("27.00");
        assertThat(MoneyKopecks.formatRubles(100)).isEqualTo("1.00");
    }
}
