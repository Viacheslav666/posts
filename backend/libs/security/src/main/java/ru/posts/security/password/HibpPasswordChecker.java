package ru.posts.security.password;

/** Optional Have I Been Pwned (k-anonymity) checker. */
public interface HibpPasswordChecker {

    boolean isPwned(String rawPassword);

    /** No-op checker used in tests and when HIBP is disabled. */
    final class NoOp implements HibpPasswordChecker {
        @Override
        public boolean isPwned(String rawPassword) {
            return false;
        }
    }
}
