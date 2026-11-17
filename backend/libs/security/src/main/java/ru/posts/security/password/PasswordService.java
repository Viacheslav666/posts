package ru.posts.security.password;

import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Argon2id hashing plus a small local breached-password denylist (HIBP optional upstream). */
public final class PasswordService {

    private static final Set<String> COMMON = Set.of(
            "password123",
            "password1234",
            "password12345",
            "1234567890",
            "12345678901",
            "qwertyuiop",
            "qwerty12345",
            "abcdefghij",
            "letmein1234",
            "welcome1234",
            "adminadmin1",
            "correcthorsebatterystaple");

    private final PasswordEncoder encoder;
    private final HibpPasswordChecker hibpPasswordChecker;
    private final boolean hibpEnabled;

    public PasswordService(HibpPasswordChecker hibpPasswordChecker, boolean hibpEnabled) {
        this.encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.hibpPasswordChecker = hibpPasswordChecker;
        this.hibpEnabled = hibpEnabled;
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    public void validateStrength(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 10 || rawPassword.length() > 128) {
            throw new WeakPasswordException("Password must be between 10 and 128 characters");
        }
        String normalized = rawPassword.toLowerCase(Locale.ROOT);
        if (COMMON.contains(normalized)) {
            throw new WeakPasswordException("Password is too common; choose another");
        }
        if (hibpEnabled && hibpPasswordChecker.isPwned(rawPassword)) {
            throw new WeakPasswordException("Password appears in known breach lists; choose another");
        }
    }

    public PasswordEncoder encoder() {
        return encoder;
    }
}
