package ru.posts.common.uuid;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RFC 9562 UUIDv7 generator (time-ordered). Preferred for all Posts primary keys.
 *
 * <p>Layout: 48-bit Unix epoch millis + version 7 + 12-bit rand_a + RFC 4122 variant + 62-bit
 * rand_b.
 */
public final class UuidV7 {

    private static final SecureRandom SECURE = new SecureRandom();

    private UuidV7() {}

    /** Cryptographically strong random bits; use for persisted entity IDs. */
    public static UUID generate() {
        return generate(System.currentTimeMillis(), SECURE);
    }

    /** Faster non-blocking generator for tests and hot paths that do not need CSPRNG. */
    public static UUID generateFast() {
        return generate(System.currentTimeMillis(), ThreadLocalRandom.current());
    }

    static UUID generate(long epochMillis, Random random) {
        if (epochMillis < 0 || epochMillis > 0xFFFF_FFFF_FFFFL) {
            throw new IllegalArgumentException("epochMillis out of 48-bit range: " + epochMillis);
        }
        long randA = random.nextInt(1 << 12) & 0x0FFFL;
        long msb = (epochMillis << 16) | (0x7L << 12) | randA;

        long randB = random.nextLong();
        long lsb = (randB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    public static boolean isUuidV7(UUID uuid) {
        return uuid.version() == 7;
    }

    /** Milliseconds since Unix epoch encoded in a UUIDv7; empty if not version 7. */
    public static long timestampMillis(UUID uuid) {
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Not a UUIDv7: version=" + uuid.version());
        }
        return uuid.getMostSignificantBits() >>> 16;
    }
}
