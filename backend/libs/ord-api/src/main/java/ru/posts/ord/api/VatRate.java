package ru.posts.ord.api;

import java.math.BigDecimal;
import java.util.Locale;

/** Supported VAT rates for ORD publisher profile and ad marking (BE-25). */
public enum VatRate {
    without_vat(null),
    rate_0(BigDecimal.ZERO),
    rate_5(new BigDecimal("5")),
    rate_7(new BigDecimal("7")),
    rate_10(new BigDecimal("10")),
    rate_20(new BigDecimal("20")),
    rate_22(new BigDecimal("22"));

    private final BigDecimal percent;

    VatRate(BigDecimal percent) {
        this.percent = percent;
    }

    /** Percent points (0/5/…/22) or {@code null} when without VAT. */
    public BigDecimal percent() {
        return percent;
    }

    public boolean isWithoutVat() {
        return this == without_vat;
    }

    /** Wire / DB value: {@code without_vat}, {@code 0}, {@code 5}, … */
    public String wireValue() {
        if (this == without_vat) {
            return "without_vat";
        }
        return percent.stripTrailingZeros().toPlainString();
    }

    public static VatRate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return without_vat;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "without_vat", "without", "none", "без", "без_ндс" -> without_vat;
            case "0", "0%", "rate_0" -> rate_0;
            case "5", "5%", "rate_5" -> rate_5;
            case "7", "7%", "rate_7" -> rate_7;
            case "10", "10%", "rate_10" -> rate_10;
            case "20", "20%", "rate_20" -> rate_20;
            case "22", "22%", "rate_22" -> rate_22;
            default -> throw new IllegalArgumentException("Unsupported VAT rate: " + raw);
        };
    }

    public static boolean isSupported(String raw) {
        try {
            parse(raw);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
