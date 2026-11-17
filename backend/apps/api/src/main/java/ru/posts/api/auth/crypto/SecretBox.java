package ru.posts.api.auth.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-256-GCM helper for small secrets (TOTP). */
public final class SecretBox {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public SecretBox(String keyMaterial) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            this.key = digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("encrypt failed", ex);
        }
    }

    public String decrypt(byte[] blob) {
        try {
            byte[] iv = Arrays.copyOfRange(blob, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(blob, 12, blob.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("decrypt failed", ex);
        }
    }
}
