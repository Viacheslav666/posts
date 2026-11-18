package ru.posts.api.ord;

/**
 * Russian INN validation — 10 (juridical) or 12 (physical/IP) digits with control checksums
 * (BE-25 / ФНС algorithm).
 */
public final class InnValidator {

    private static final int[] COEFF_10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] COEFF_12_N11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] COEFF_12_N12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    private InnValidator() {}

    public static boolean isValid(String inn) {
        if (inn == null) {
            return false;
        }
        String digits = inn.trim();
        if (digits.length() != 10 && digits.length() != 12) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
        }
        if (digits.length() == 10) {
            return checkDigit(digits, COEFF_10, 9);
        }
        return checkDigit(digits, COEFF_12_N11, 10) && checkDigit(digits, COEFF_12_N12, 11);
    }

    /**
     * Foreign counterparties may use non-RU tax ids; when {@code foreign} is true only non-blank
     * length ≤ 32 is required (checksum skipped).
     */
    public static boolean isValid(String inn, boolean foreign) {
        if (foreign) {
            if (inn == null) {
                return false;
            }
            String value = inn.trim();
            return !value.isEmpty() && value.length() <= 32;
        }
        return isValid(inn);
    }

    public static void requireValid(String inn) {
        if (!isValid(inn)) {
            throw new IllegalArgumentException("INN must be 10 or 12 digits with valid checksum");
        }
    }

    private static boolean checkDigit(String digits, int[] coeffs, int checkIndex) {
        int sum = 0;
        for (int i = 0; i < coeffs.length; i++) {
            sum += coeffs[i] * (digits.charAt(i) - '0');
        }
        int control = (sum % 11) % 10;
        return control == (digits.charAt(checkIndex) - '0');
    }
}
