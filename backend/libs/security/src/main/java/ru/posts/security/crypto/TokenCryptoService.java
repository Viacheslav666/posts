package ru.posts.security.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope encryption for social tokens (ADR-0006). Plaintext is never stored on entities —
 * callers seal/unseal explicitly around adapter calls.
 *
 * <p>Blob layout: {@code version(1) | nonce(12) | ciphertext+tag}.
 */
public final class TokenCryptoService {

    public static final byte BLOB_VERSION = 1;
    private static final int NONCE_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final byte[] kek;

    public TokenCryptoService(byte[] kek) {
        Objects.requireNonNull(kek, "kek");
        if (kek.length != 32) {
            throw new IllegalArgumentException("KEK must be 32 bytes (AES-256)");
        }
        this.kek = Arrays.copyOf(kek, kek.length);
    }

    /** Derives a 32-byte KEK from arbitrary key material via SHA-256. */
    public static TokenCryptoService fromKeyMaterial(String keyMaterial) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return new TokenCryptoService(digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public byte[] generateDek() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_KEY_BITS, random);
            SecretKey key = keyGen.generateKey();
            return key.getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("DEK generation failed", ex);
        }
    }

    public byte[] wrapDek(byte[] dek) {
        return sealWithKey(kek, dek);
    }

    public byte[] unwrapDek(byte[] wrappedDek) {
        return unsealWithKey(kek, wrappedDek);
    }

    public byte[] seal(byte[] dek, String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        return sealWithKey(dek, plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public String unseal(byte[] dek, byte[] blob) {
        byte[] plain = unsealWithKey(dek, blob);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private byte[] sealWithKey(byte[] keyBytes, byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_LEN];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer buffer = ByteBuffer.allocate(1 + NONCE_LEN + ciphertext.length);
            buffer.put(BLOB_VERSION);
            buffer.put(nonce);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("seal failed", ex);
        }
    }

    private byte[] unsealWithKey(byte[] keyBytes, byte[] blob) {
        if (blob == null || blob.length < 1 + NONCE_LEN + 16) {
            throw new IllegalArgumentException("ciphertext blob too short");
        }
        if (blob[0] != BLOB_VERSION) {
            throw new IllegalArgumentException("unsupported blob version: " + blob[0]);
        }
        try {
            byte[] nonce = Arrays.copyOfRange(blob, 1, 1 + NONCE_LEN);
            byte[] ciphertext = Arrays.copyOfRange(blob, 1 + NONCE_LEN, blob.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("unseal failed", ex);
        }
    }
}
