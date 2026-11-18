package ru.posts.ord.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VatCalculatorTest {

    @Test
    void splitsAllSupportedRates() {
        assertSplit("120.00", "20", "100.00", "20.00");
        assertSplit("105.00", "5", "100.00", "5.00");
        assertSplit("107.00", "7", "100.00", "7.00");
        assertSplit("110.00", "10", "100.00", "10.00");
        assertSplit("122.00", "22", "100.00", "22.00");
        assertSplit("100.00", "0", "100.00", "0.00");
        assertSplit("100.00", "without_vat", "100.00", "0.00");
    }

    private static void assertSplit(String gross, String rate, String net, String vat) {
        VatCalculator.Breakdown b = VatCalculator.fromAmountWithVat(new BigDecimal(gross), rate);
        assertThat(b.amountWithoutVat()).isEqualByComparingTo(net);
        assertThat(b.vatAmount()).isEqualByComparingTo(vat);
        assertThat(b.amountWithVat()).isEqualByComparingTo(gross);
    }
}
