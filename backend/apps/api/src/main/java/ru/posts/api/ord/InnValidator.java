package ru.posts.api.ord;

/** Russian INN validation — 10 (juridical) or 12 (physical/IP) digits. */
public final class InnValidator {

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
        return true;
    }

    public static void requireValid(String inn) {
        if (!isValid(inn)) {
            throw new IllegalArgumentException("INN must be 10 or 12 digits");
        }
    }
}
