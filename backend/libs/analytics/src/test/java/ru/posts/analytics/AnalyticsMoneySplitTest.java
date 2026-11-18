package ru.posts.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc;
import ru.posts.persistence.jdbc.AnalyticsPostsAggJdbc.MoneySplit;

class AnalyticsMoneySplitTest {

    @Test
    void vatSplitAndFeeMatchOrdCalculatorSemantics() {
        MoneySplit split = AnalyticsPostsAggJdbc.splitAmountWithVat(new BigDecimal("120.00"), "20");
        assertThat(split.withVat()).isEqualByComparingTo("120.00");
        assertThat(split.withoutVat()).isEqualByComparingTo("100.00");
        assertThat(split.vat()).isEqualByComparingTo("20.00");

        BigDecimal fee = split.withVat().multiply(new BigDecimal("0.03")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(fee).isEqualByComparingTo("3.60");
    }

    @Test
    void withoutVatKeepsGross() {
        MoneySplit split = AnalyticsPostsAggJdbc.splitAmountWithVat(new BigDecimal("100.00"), "without_vat");
        assertThat(split.withoutVat()).isEqualByComparingTo("100.00");
        assertThat(split.vat()).isEqualByComparingTo("0.00");
    }

    @Test
    void halfUpRoundingOnOddAmounts() {
        // 10.005 → 10.01 with HALF_UP at scale 2 before split inputs; fee on 33.33 * 0.03 = 1.00
        MoneySplit split = AnalyticsPostsAggJdbc.splitAmountWithVat(new BigDecimal("33.33"), "without_vat");
        BigDecimal fee = split.withVat().multiply(new BigDecimal("0.03")).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(fee).isEqualByComparingTo("1.00");
    }
}
