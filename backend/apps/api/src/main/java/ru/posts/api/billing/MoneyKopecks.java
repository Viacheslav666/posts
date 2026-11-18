package ru.posts.api.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for RUB ↔ kopecks conversion used by balance billing. */
public final class MoneyKopecks {

    private MoneyKopecks() {}

    public static long toKopecks(BigDecimal rubles) {
        if (rubles == null) {
            return 0L;
        }
        return rubles.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal toRubles(long kopecks) {
        return BigDecimal.valueOf(kopecks, 2);
    }

    public static String formatRubles(long kopecks) {
        return toRubles(kopecks).toPlainString();
    }
}
