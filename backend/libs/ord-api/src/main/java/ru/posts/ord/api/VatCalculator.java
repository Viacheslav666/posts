package ru.posts.ord.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Server-side VAT split for post/contract amounts (BE-25). Amounts are treated as <em>including</em>
 * VAT when a taxable rate is set.
 */
public final class VatCalculator {

    private static final int SCALE = 2;

    private VatCalculator() {}

    public record Breakdown(
            BigDecimal amountWithVat, BigDecimal amountWithoutVat, BigDecimal vatAmount, String vatRate) {}

    public static Breakdown fromAmountWithVat(BigDecimal amountWithVat, String vatRateWire) {
        VatRate rate = VatRate.parse(vatRateWire);
        BigDecimal gross = amountWithVat == null
                ? BigDecimal.ZERO.setScale(SCALE)
                : amountWithVat.setScale(SCALE, RoundingMode.HALF_UP);
        if (rate.isWithoutVat() || rate.percent() == null || rate.percent().signum() == 0) {
            BigDecimal vat = BigDecimal.ZERO.setScale(SCALE);
            return new Breakdown(gross, gross, vat, rate.wireValue());
        }
        BigDecimal hundred = new BigDecimal("100");
        BigDecimal divisor = hundred.add(rate.percent());
        BigDecimal net = gross.multiply(hundred).divide(divisor, SCALE, RoundingMode.HALF_UP);
        BigDecimal vat = gross.subtract(net).setScale(SCALE, RoundingMode.HALF_UP);
        return new Breakdown(gross, net, vat, rate.wireValue());
    }
}
